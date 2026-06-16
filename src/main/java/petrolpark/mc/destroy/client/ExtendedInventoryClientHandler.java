package petrolpark.mc.destroy.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.config.DestroyClientConfigs;
import petrolpark.mc.destroy.config.DestroyClientConfigs.ExtraInventoryClientSettings;
import petrolpark.mc.destroy.config.DestroyConfigs;
import petrolpark.mc.destroy.core.extendedinventory.ExtendedInventory;
import petrolpark.mc.destroy.core.extendedinventory.ExtraInventorySizeChangeS2CPacket;
import petrolpark.mc.destroy.core.extendedinventory.IExtendedInventoryMenu;
import petrolpark.mc.destroy.core.extendedinventory.IExtendedInventoryScreen;
import petrolpark.mc.destroy.core.extendedinventory.RequestInventoryFullStateC2SPacket;

/**
 * Client-side rendering + input handling for the Extended Inventory's HUD layer (extra hotbar
 * slots that appear to the left/right of the vanilla hotbar). Lives separately from the data
 * layer ({@link ExtendedInventory} — server + client shared) and the network layer
 *.
 *
 * <p>Behavior:</p>
 * <ul>
 * <li>Extra hotbar slots appear in the corners of the HUD (configurable left/right
 * distribution via {@link DestroyConfigs.client extraHotbarSlotLocation} +
 * {@link DestroyConfigs.client extraHotbarPrioritySlotCount}).</li>
 * <li>Pressing the assigned hotbar number keys (vanilla 1-9 + Destroy {@code DestroyKeys.HOTBAR_SLOT_9..16})
 * cycles the {@code selected} slot through both vanilla AND extra hotbar slots.</li>
 * <li>Items in extra hotbar slots are rendered + held in hand normally — pickup overflow
 * (from {@link ExtendedInventory#add overflow into extra slots}) is now player-accessible.</li>
 * </ul>
*/
@EventBusSubscriber(value = Dist.CLIENT, modid = Destroy.MOD_ID)
public class ExtendedInventoryClientHandler {

    /** Vanilla 1-9 KeyMappings + Destroy 10-17 — populated on first tick after key registration.*/
    private static final List<KeyMapping> HOTBAR_KEYS = new ArrayList<>(17);
    private static boolean HOTBAR_KEYS_INITIALIZED = false;

    // ---- Screen-integration state. The "current screen" + Rect2i panel coords are
    // refreshed on ScreenEvent.Init.Post + invalidated on ScreenEvent.Closing. Static because
    // there's only one client → one screen at a time.

    /** Cached config snapshot — refreshed in {@link #onClientTick}; if it changes we rebuild
 * the client inventory menu so panel positions reflect the new settings.*/
    private static ExtraInventoryClientSettings settings = null;
    /** The {@link AbstractContainerScreen} currently being rendered with extra slots, or null.*/
    private static AbstractContainerScreen<?> currentScreen = null;
    /** Cached Rect2i regions — null when that piece doesn't render for this screen.*/
    private static Rect2i leftHotbar = null;
    private static Rect2i rightHotbar = null;
    private static Rect2i combinedInventoryHotbar = null;
    private static Rect2i inventory = null;
    /** Areas covered by extra-inventory panels (for JEI to avoid rendering over them).*/
    private static List<Rect2i> extraGuiAreas = Collections.emptyList();

    /** Padding inside an extra-inventory "window" (border to slot).*/
    public static final int INVENTORY_PADDING = 7;
    /** Horizontal gap between vanilla inventory window and extra-inventory window.*/
    public static final int INVENTORY_SPACING = 4;
    /** Vertical gap between main extra-inventory panel and extra-hotbar panel.*/
    public static final int INVENTORY_HOTBAR_SPACING = 4;

    /** Tick the Extended Inventory client-side: consume hotbar key presses + refresh menu on
 * config change.*/
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ExtendedInventory inv;
        try {
            inv = ExtendedInventory.get(mc.player);
        } catch (ClassCastException ex) {
            // Player.inventory wasn't replaced — the mixin failed or the player is some non-vanilla
            // entity that doesn't carry the ExtendedInventory subclass. Skip the tick rather than crashing.
            return;
        }

        // config changed → rebuild client inventoryMenu with new geometry.
        // only rebuild on ACTUAL config change, not on first-time-init when settings
        // is still null. The first-time refresh would replace the just-built
        // PlayerInventoryMixin menu with a different instance, breaking server-client menu sync.
        ExtraInventoryClientSettings currentSettings = DestroyConfigs.client().getExtraInventorySettings();
        if (!currentSettings.equals(settings)) {
            boolean firstInit = (settings == null);
            settings = currentSettings;
            if (!firstInit) refreshClientInventoryMenu(inv);
        }

        // Initialize Key Mappings (deferred: vanilla mc.options is null until client setup
        // completes). Run once + cache.
        if (!HOTBAR_KEYS_INITIALIZED) {
            Collections.addAll(HOTBAR_KEYS, mc.options.keyHotbarSlots);
            Collections.addAll(HOTBAR_KEYS,
                DestroyKeys.HOTBAR_SLOT_9.keybind, DestroyKeys.HOTBAR_SLOT_10.keybind,
                DestroyKeys.HOTBAR_SLOT_11.keybind, DestroyKeys.HOTBAR_SLOT_12.keybind,
                DestroyKeys.HOTBAR_SLOT_13.keybind, DestroyKeys.HOTBAR_SLOT_14.keybind,
                DestroyKeys.HOTBAR_SLOT_15.keybind, DestroyKeys.HOTBAR_SLOT_16.keybind);
            HOTBAR_KEYS_INITIALIZED = true;
        }

        // Allow switching to extended hotbar slots — but only when no overlay/screen is open
        // and the hotbar-save / hotbar-load activator keys aren't held.
        if (mc.getOverlay() != null || mc.screen != null
            || mc.options.keyLoadHotbarActivator.isDown()
            || mc.options.keySaveHotbarActivator.isDown()) return;
        int hotbarSize = inv.getHotbarSize();
        for (int i = 0; i < hotbarSize && i < HOTBAR_KEYS.size(); i++) {
            if (HOTBAR_KEYS.get(i).consumeClick()) {
                int slot = i - DestroyClientConfigs.getLeftSlots(inv.getExtraHotbarSlots());
                if (slot < 0) slot += hotbarSize;
                int slotIndex = inv.getSlotIndex(slot);
                if (slotIndex >= 0) inv.selected = slotIndex;
            }
        }
    }

    /** Render the 9-slice borders for the extra hotbar slot panels (left + right).*/
    public static void renderExtraHotbarBackground(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || mc.options.hideGui || gameMode == null
            || gameMode.getPlayerMode() == GameType.SPECTATOR) return;

        ExtendedInventory inv;
        try {
            inv = ExtendedInventory.get(player);
        } catch (ClassCastException ex) { return; }
        int extraSlots = inv.getExtraHotbarSlots();
        if (extraSlots == 0) return;

        PoseStack ms = graphics.pose();
        int y = graphics.guiHeight() - 22;
        RenderSystem.enableDepthTest();

        for (boolean right : Iterate.trueAndFalse) {
            int x = graphics.guiWidth() / 2 - 91;
            int slotCount;
            if (right) {
                slotCount = DestroyClientConfigs.getRightSlots(extraSlots);
                x += 9 * 20;
            } else {
                slotCount = DestroyClientConfigs.getLeftSlots(extraSlots);
                x -= slotCount * 20;
            }
            ms.pushPose();
            if (slotCount > 0) DestroyNineSlices.HOTBAR.render(graphics, x, y, 2 + slotCount * 20, 22);
            ms.popPose();
        }
    }

    /** Render the slot icons + items + selected-slot highlight for the extra hotbar slots.*/
    public static void renderExtraHotbar(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || mc.options.hideGui || gameMode == null
            || gameMode.getPlayerMode() == GameType.SPECTATOR) return;

        ExtendedInventory inv;
        try {
            inv = ExtendedInventory.get(player);
        } catch (ClassCastException ex) { return; }
        int extraSlots = inv.getExtraHotbarSlots();
        if (extraSlots == 0) return;

        PoseStack ms = graphics.pose();
        int y = graphics.guiHeight() - 21;
        RenderSystem.enableDepthTest();

        int item = 0;
        for (boolean right : Iterate.trueAndFalse) {
            int slotCount;
            int x = graphics.guiWidth() / 2 - 90;
            if (right) {
                slotCount = DestroyClientConfigs.getRightSlots(extraSlots);
                x += 9 * 20;
            } else {
                slotCount = DestroyClientConfigs.getLeftSlots(extraSlots);
                x -= slotCount * 20;
            }
            if (slotCount == 0) continue;

            ms.pushPose();
            for (int i = 0; i < slotCount; i++) {
                DestroyGuiTextures.HOTBAR_SLOT.render(graphics, x + i * 20, y);
                // Fixed seed (42069). The seed parameter
                // is for animated stack overlay timing (e.g. enchantment glint) — using a fixed
                // value keeps the animation phase constant for these slots, fine for HUD render.
                mc.gui.renderSlot(graphics, 2 + x + i * 20, y + 2, deltaTracker, player,
                    inv.extraItems.get(item), 42069);
                item++;
            }
            ms.popPose();
        }

        // Selected-slot highlight — re-render over the extra hotbar if an extra slot is selected.
        ms.pushPose();
        int selected = inv.getSelectedHotbarIndex();
        int selectedX = graphics.guiWidth() / 2 - 92;
        if (selected >= Inventory.getSelectionSize() + DestroyClientConfigs.getRightSlots(extraSlots)) {
            // A LEFT extra slot is selected — shift to the left side of the hotbar.
            selectedX -= DestroyClientConfigs.getLeftSlots(extraSlots) * 20;
            selected -= (Inventory.getSelectionSize() + DestroyClientConfigs.getRightSlots(extraSlots));
        }
        selectedX += selected * 20;
        graphics.blitSprite(Gui.HOTBAR_SELECTION_SPRITE, selectedX, y - 2, 24, 23);
        ms.popPose();
    }

    // ---- Geometry helpers for placing extra-inventory panels around an open Screen.
    // Coordinate system is screen-relative
    // (origin at upper-left of the Screen, positive y down).

    /** Top-left of the LEFT extra-hotbar window (the bracket that wraps the slot row).
 * @return null if no left-side hotbar slots configured.*/
    public static Rect2i getLeftHotbarLocation(ExtendedInventory inv, Rect2i screenArea, int hotbarY) {
        int slots = DestroyClientConfigs.getLeftSlots(inv.getExtraHotbarSlots());
        if (slots == 0) return null;
        return getHotbarLocation(screenArea, hotbarY,
            -INVENTORY_SPACING - 2 * INVENTORY_PADDING - slots * 18, slots);
    }

    /** Top-left of the RIGHT extra-hotbar window. @return null if no right-side hotbar slots.*/
    public static Rect2i getRightHotbarLocation(ExtendedInventory inv, Rect2i screenArea, int hotbarY) {
        int slots = DestroyClientConfigs.getRightSlots(inv.getExtraHotbarSlots());
        if (slots == 0) return null;
        return getHotbarLocation(screenArea, hotbarY,
            screenArea.getWidth() + INVENTORY_SPACING, slots);
    }

    /** Padded hotbar window rect.*/
    protected static Rect2i getHotbarLocation(Rect2i screenArea, int hotbarY, int xOffset, int slots) {
        return new Rect2i(
            screenArea.getX() + xOffset,
            hotbarY - INVENTORY_PADDING,
            2 * INVENTORY_PADDING + slots * 18,
            2 * INVENTORY_PADDING + 18);
    }

    /** Combined window when the main extra-inventory panel is tall enough (>=3 rows) to merge
 * with the same-side hotbar panel into one continuous window. @return null if separate
 * windows should be used instead.*/
    public static Rect2i getCombinedInventoryHotbarLocation(ExtendedInventory inv, Rect2i screenArea, int hotbarY) {
        boolean left = DestroyConfigs.client().extraInventoryLeft.get();
        int inventorySlots = inv.extraItems.size() - inv.getExtraHotbarSlots();
        int inventoryWidth = DestroyConfigs.client().extraInventoryWidth.get();
        int inventoryHeight = inventorySlots / inventoryWidth;
        if (inventorySlots % inventoryWidth > 0) inventoryHeight++;
        int hotbarSlots = left ? DestroyClientConfigs.getLeftSlots(inv.getExtraHotbarSlots())
                                : DestroyClientConfigs.getRightSlots(inv.getExtraHotbarSlots());
        int width = Math.max(inventoryWidth, hotbarSlots);
        if (hotbarSlots > 0 && inventoryHeight >= 3) {
            return new Rect2i(
                screenArea.getX() + (left ? -INVENTORY_SPACING - 2 * INVENTORY_PADDING - width * 18
                                          : INVENTORY_SPACING + screenArea.getWidth()),
                hotbarY - INVENTORY_HOTBAR_SPACING - 18 * inventoryHeight - INVENTORY_PADDING,
                2 * INVENTORY_PADDING + width * 18,
                18 * (inventoryHeight + 1) + 2 * INVENTORY_PADDING + INVENTORY_HOTBAR_SPACING);
        }
        return null;
    }

    /** Main (non-hotbar) extra-inventory panel. @return null if there are no non-hotbar slots.*/
    public static Rect2i getInventoryLocation(ExtendedInventory inv, Rect2i screenArea, int hotbarY) {
        int inventorySlots = inv.extraItems.size() - inv.getExtraHotbarSlots();
        if (inventorySlots <= 0) return null;
        int inventoryWidth = DestroyConfigs.client().extraInventoryWidth.get();
        int inventoryHeight = inventorySlots / inventoryWidth;
        if (inventorySlots % inventoryWidth > 0) inventoryHeight++;
        return new Rect2i(
            screenArea.getX() + (DestroyConfigs.client().extraInventoryLeft.get()
                ? -INVENTORY_SPACING - 2 * INVENTORY_PADDING - inventoryWidth * 18
                : INVENTORY_SPACING + screenArea.getWidth()),
            hotbarY - INVENTORY_HOTBAR_SPACING - INVENTORY_PADDING - 18 * Math.max(3, inventoryHeight),
            2 * INVENTORY_PADDING + inventoryWidth * 18,
            2 * INVENTORY_PADDING + 18 * inventoryHeight);
    }

    /** Maximum space taken by a Screen including any Create "extra areas" (sticky-out panels).*/
    public static Rect2i getScreenArea(AbstractContainerScreen<?> screen) {
        Rect2i area = new Rect2i(0, 0, screen.getXSize(), screen.getYSize());
        if (screen instanceof AbstractSimiContainerScreen<?> simiScreen) {
            for (Rect2i extraArea : simiScreen.getExtraAreas()) {
                area.setX(Math.min(area.getX(), extraArea.getX()));
                area.setWidth(Math.max(area.getWidth(),
                    extraArea.getX() - area.getX() + extraArea.getWidth()));
            }
        }
        return area;
    }

    /** Find Y coord of the hotbar row in the given Screen (so extra panels align to it).*/
    public static int findHotbarY(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        int hotbarY = screen.height - 22;
        if (player == null) return hotbarY;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == player.getInventory() && Inventory.isHotbarSlot(slot.getSlotIndex())) {
                return slot.y;
            }
        }
        return hotbarY;
    }

    /** Recompute the Rect2i panel positions from current screen + inv state. Called on screen
 * open + on inventory size change.*/
    public static void refreshExtraInventoryAreas(ExtendedInventory inv) {
        if (currentScreen == null) return;
        boolean mainInventoryLeft = DestroyConfigs.client().extraInventoryLeft.get();

        if (currentScreen instanceof IExtendedInventoryScreen customScreen
            && !customScreen.customExtendedInventoryRendering()) {
            int leftHotbarSlots = DestroyClientConfigs.getLeftSlots(inv.getExtraHotbarSlots());
            leftHotbar = customScreen.getLeftHotbarLocation(inv, leftHotbarSlots, mainInventoryLeft);
            rightHotbar = customScreen.getRightHotbarLocation(inv, leftHotbarSlots, mainInventoryLeft);
            combinedInventoryHotbar = customScreen.getCombinedInventoryHotbarLocation(inv, leftHotbarSlots, mainInventoryLeft);
            inventory = customScreen.getInventoryLocation(inv, leftHotbarSlots, mainInventoryLeft);
            extraGuiAreas = customScreen.getExtendedInventoryGuiAreas(inv);
        } else {
            Rect2i screenArea = getScreenArea(currentScreen);
            int hotbarY = findHotbarY(currentScreen);
            leftHotbar = getLeftHotbarLocation(inv, screenArea, hotbarY);
            rightHotbar = getRightHotbarLocation(inv, screenArea, hotbarY);
            combinedInventoryHotbar = getCombinedInventoryHotbarLocation(inv, screenArea, hotbarY);
            inventory = getInventoryLocation(inv, screenArea, hotbarY);
            extraGuiAreas = new ArrayList<>(3);
            int gx = currentScreen.getGuiLeft();
            int gy = currentScreen.getGuiTop();
            if (combinedInventoryHotbar == null) {
                if (inventory != null) extraGuiAreas.add(offset(inventory, gx, gy));
                if (leftHotbar != null) extraGuiAreas.add(offset(leftHotbar, gx, gy));
                if (rightHotbar != null) extraGuiAreas.add(offset(rightHotbar, gx, gy));
            } else {
                extraGuiAreas.add(offset(combinedInventoryHotbar, gx, gy));
                Rect2i renderedHotbar = mainInventoryLeft ? rightHotbar : leftHotbar;
                if (renderedHotbar != null) extraGuiAreas.add(offset(renderedHotbar, gx, gy));
            }
        }
    }

    private static Rect2i offset(Rect2i rect, int x, int y) {
        return new Rect2i(rect.getX() + x, rect.getY() + y, rect.getWidth(), rect.getHeight());
    }

    /** Rebuild the survival inventory menu with extra slots positioned per current screen geometry.
 * Calls {@link ExtendedInventory#refreshPlayerInventoryMenu} which reassigns
 * {@link Player#inventoryMenu} (requires the {@code public-f} AT on that field).*/
    public static void refreshClientInventoryMenu(ExtendedInventory inv) {
        Rect2i screenArea = new Rect2i(0, 0, 176, 166);  // Default vanilla InventoryScreen size
        Rect2i leftHb = getLeftHotbarLocation(inv, screenArea, 142);
        Rect2i rightHb = getRightHotbarLocation(inv, screenArea, 142);
        Rect2i combinedHb = getCombinedInventoryHotbarLocation(inv, screenArea, 142);
        Rect2i invRect = combinedHb == null ? getInventoryLocation(inv, screenArea, 142) : combinedHb;
        int invX = invRect == null ? 0 : invRect.getX();
        int invY = invRect == null ? 0 : invRect.getY();
        int leftX = leftHb == null ? 0 : leftHb.getX() + INVENTORY_PADDING;
        int leftY = leftHb == null ? 0 : leftHb.getY() + INVENTORY_PADDING;
        int rightX = rightHb == null ? 0 : rightHb.getX() + INVENTORY_PADDING;
        int rightY = rightHb == null ? 0 : rightHb.getY() + INVENTORY_PADDING;
        ExtendedInventory.refreshPlayerInventoryMenu(inv.player,
            DestroyConfigs.client().extraInventoryWidth.get(),
            invX + INVENTORY_PADDING, invY + INVENTORY_PADDING,
            DestroyClientConfigs.getLeftSlots(inv.getExtraHotbarSlots()),
            leftX, leftY, rightX, rightY);
    }

    /** Apply server-side size change packet to client inventory + request a full state resync.*/
    public static void handleExtendedInventorySizeChange(ExtraInventorySizeChangeS2CPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ExtendedInventory inv;
        try {
            inv = ExtendedInventory.get(mc.player);
        } catch (ClassCastException ex) { return; }
        inv.setExtraInventorySize(packet.extraInventorySize());
        inv.setExtraHotbarSlots(packet.extraHotbarSlots());
        refreshClientInventoryMenu(inv);
        if (packet.requestFullState()) {
            CatnipServices.NETWORK.sendToServer(RequestInventoryFullStateC2SPacket.INSTANCE);
        }
    }

    /** Add Slots corresponding to those of the Extended Inventory to a Menu (client-side).*/
    public static void addSlotsToClientMenu(ExtendedInventory inv, AbstractContainerMenu menu) {
        addSlotsToClientMenu(inv, menu::addSlot, Slot::new);
    }

    /** Used by the Creative-screen mixin
 * which needs to wrap each Slot in {@code CreativeModeInventoryScreen.SlotWrapper} so
 * the Creative menu's per-slot interaction logic still applies.*/
    public static void addSlotsToClientMenu(ExtendedInventory inv,
        java.util.function.Consumer<Slot> slotAdder,
        ExtendedInventory.SlotFactory slotFactory) {
        Rect2i invRect = combinedInventoryHotbar == null ? inventory : combinedInventoryHotbar;
        int invX = invRect == null ? 0 : invRect.getX();
        int invY = invRect == null ? 0 : invRect.getY();
        int leftX = leftHotbar == null ? 0 : leftHotbar.getX() + INVENTORY_PADDING;
        int leftY = leftHotbar == null ? 0 : leftHotbar.getY() + INVENTORY_PADDING;
        int rightX = rightHotbar == null ? 0 : rightHotbar.getX() + INVENTORY_PADDING;
        int rightY = rightHotbar == null ? 0 : rightHotbar.getY() + INVENTORY_PADDING;
        inv.addExtraInventorySlotsToMenu(slotAdder, slotFactory,
            DestroyConfigs.client().extraInventoryWidth.get(),
            invX + INVENTORY_PADDING, invY + INVENTORY_PADDING,
            DestroyClientConfigs.getLeftSlots(inv.getExtraHotbarSlots()),
            leftX, leftY, rightX, rightY);
    }

    /**
 * When the Creative menu opens its survival tab, the mixin needs to register itself as the
 * current screen so geometry-recompute hooks pick it up.*/
    public static void setCurrentScreen(AbstractContainerScreen<?> screen) {
        currentScreen = screen;
    }

    /** When a Screen opens: cache it as currentScreen + recompute panel rects. For non-vanilla
 * menus that aren't IExtendedInventoryMenu / Creative / vanilla survival, also adds extra
 * slots to the menu directly (vanilla survival is handled via the Player inventoryMenu
 * reassignment in {@link #refreshClientInventoryMenu}).*/
    @SubscribeEvent
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            currentScreen = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        ExtendedInventory inv;
        try {
            inv = ExtendedInventory.get(player);
        } catch (ClassCastException ex) { return; }
        AbstractContainerMenu menu = screen.getMenu();

        if (menu == player.inventoryMenu) {
            // Just-opened survival inventory — request a full state broadcast in case the
            // local menu is stale (e.g. re-built after a size change).
            CatnipServices.NETWORK.sendToServer(RequestInventoryFullStateC2SPacket.INSTANCE);
        }

        if (!ExtendedInventory.supportsExtraInventory(menu)
            && !(menu == player.inventoryMenu || screen instanceof CreativeModeInventoryScreen)) {
            currentScreen = null;
            return;
        }

        if (screen != currentScreen
            && !(screen instanceof InventoryScreen && currentScreen instanceof CreativeModeInventoryScreen)) {
            // Don't override Creative Mode Screen with the InventoryScreen that also gets opened
            currentScreen = screen;
            refreshExtraInventoryAreas(inv);
        }

        // For non-vanilla, non-Creative, non-IExtendedInventoryMenu menus, slots are added
        // post-hoc here. (The other 3 paths add slots elsewhere: vanilla via Player
        // inventoryMenu reassignment, Creative via the Creative-screen mixin path,
        // IExtendedInventoryMenu screens add their own slots.)
        if (!(menu == player.inventoryMenu
            || screen instanceof CreativeModeInventoryScreen
            || menu instanceof IExtendedInventoryMenu)) {
            addSlotsToClientMenu(inv, menu);
        }
    }

    /** Render extra-inventory panel backgrounds + per-slot backgrounds inside the open Screen.*/
    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || screen != currentScreen) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        if (!(
            screen.getMenu() == player.inventoryMenu
            || ExtendedInventory.supportsExtraInventory(screen.getMenu())
            || (screen instanceof CreativeModeInventoryScreen
                && CreativeModeInventoryScreen.selectedTab.getType() == CreativeModeTab.Type.INVENTORY)
        ) || (
            screen instanceof IExtendedInventoryScreen customScreen
                && customScreen.customExtendedInventoryRendering()
        )) return;

        ExtendedInventory inv;
        try {
            inv = ExtendedInventory.get(player);
        } catch (ClassCastException ex) { return; }
        boolean left = DestroyConfigs.client().extraInventoryLeft.get();
        int leftHotbarSlots = DestroyClientConfigs.getLeftSlots(inv.getExtraHotbarSlots());
        int columns = DestroyConfigs.client().extraInventoryWidth.get();
        GuiGraphics graphics = event.getGuiGraphics();
        PoseStack ms = graphics.pose();

        RenderSystem.enableDepthTest();
        ms.pushPose();
        try {
            ms.translate(screen.getGuiLeft(), screen.getGuiTop(), 2f);
            ms.pushPose();
            ms.translate(-1f, -1f, 0f);
            // Panel backgrounds (9-slice).
            if (combinedInventoryHotbar != null) {
                DestroyNineSlices.INVENTORY_BACKGROUND.render(graphics, combinedInventoryHotbar);
                Rect2i renderedHotbar = left ? rightHotbar : leftHotbar;
                if (renderedHotbar != null) DestroyNineSlices.INVENTORY_BACKGROUND.render(graphics, renderedHotbar);
            } else {
                if (inventory != null) DestroyNineSlices.INVENTORY_BACKGROUND.render(graphics, inventory);
                if (leftHotbar != null) DestroyNineSlices.INVENTORY_BACKGROUND.render(graphics, leftHotbar);
                if (rightHotbar != null) DestroyNineSlices.INVENTORY_BACKGROUND.render(graphics, rightHotbar);
            }
            ms.popPose();
            // Per-slot dot backgrounds.
            if (leftHotbar != null) for (int i = 0; i < leftHotbarSlots; i++) {
                DestroyGuiTextures.INVENTORY_SLOT.render(graphics,
                    leftHotbar.getX() + INVENTORY_PADDING - 1 + i * 18,
                    leftHotbar.getY() + INVENTORY_PADDING - 1);
            }
            int j = 0;
            if (rightHotbar != null) for (int i = leftHotbarSlots; i < inv.getExtraHotbarSlots(); i++) {
                DestroyGuiTextures.INVENTORY_SLOT.render(graphics,
                    rightHotbar.getX() + INVENTORY_PADDING - 1 + j * 18,
                    rightHotbar.getY() + INVENTORY_PADDING - 1);
                j++;
            }
            j = 0;
            Rect2i invRect = combinedInventoryHotbar == null ? inventory : combinedInventoryHotbar;
            if (invRect != null) for (int i = inv.getExtraHotbarSlots(); i < inv.extraItems.size(); i++) {
                DestroyGuiTextures.INVENTORY_SLOT.render(graphics,
                    invRect.getX() + INVENTORY_PADDING - 1 + 18 * (j % columns),
                    invRect.getY() + INVENTORY_PADDING - 1 + (j / columns) * 18);
                j++;
            }
        } finally {
            ms.popPose();
        }
    }

    /** When a Screen closes: forget which screen had extra slots.*/
    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        currentScreen = null;
    }

    /** JEI / recipe-book displacement helper — returns areas covered by extra panels.*/
    public static List<Rect2i> getGuiExtraAreas() {
        if (currentScreen == null) return Collections.emptyList();
        return extraGuiAreas;
    }

    /** Register the two HUD layers (background border + slot icons/items/selection) above and
 * below vanilla's HOTBAR layer. NeoForge 1.21 auto-routes this to the mod event bus by
 * event class.*/
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.HOTBAR, Destroy.asResource("extra_hotbar_background"),
            ExtendedInventoryClientHandler::renderExtraHotbarBackground);
        event.registerAbove(VanillaGuiLayers.HOTBAR, Destroy.asResource("extra_hotbar"),
            ExtendedInventoryClientHandler::renderExtraHotbar);
    }
}
