package petrolpark.mc.destroy.client;

import com.tterrag.registrate.util.entry.MenuEntry;

import net.neoforged.bus.api.IEventBus;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.content.redstone.programmer.RedstoneProgrammerMenu;
import petrolpark.mc.destroy.content.redstone.programmer.RedstoneProgrammerScreen;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveMenu;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveScreen;

/**
 * Central registry of Destroy's custom {@link net.minecraft.world.inventory.MenuType}s with their
 * paired client-side Screens. Each entry uses Registrate's {@code .menu(name, factory, screenFactory)}
 * fluent chain, which registers both the MenuType (server) and the Screen binding (client) atomically.
*/
public class DestroyMenuTypes {

    public static final MenuEntry<RedstoneProgrammerMenu> REDSTONE_PROGRAMMER =
        Destroy.REGISTRATE.menu(
            "redstone_programmer",
            RedstoneProgrammerMenu::new,
            () -> RedstoneProgrammerScreen::new)
        .register();

    // Mixed explosive crafting menu, paired with MixedExplosiveScreen.
    public static final MenuEntry<MixedExplosiveMenu> CUSTOM_EXPLOSIVE =
        Destroy.REGISTRATE.menu(
            "custom_explosive",
            MixedExplosiveMenu::new,
            () -> MixedExplosiveScreen::new)
        .register();

    /** Class-load trigger — call from {@link Destroy#Destroy} to force field init.*/
    public static void register() {}

    /** 1.21
 * Registrate auto-wires to mod bus via {@code Destroy.REGISTRATE}. Kept for API compat in
 * case some upstream code references it.
*/
    @Deprecated
    public static void register(IEventBus modEventBus) {}
}
