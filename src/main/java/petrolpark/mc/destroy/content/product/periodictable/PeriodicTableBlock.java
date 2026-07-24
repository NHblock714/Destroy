package petrolpark.mc.destroy.content.product.periodictable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyAdvancementTrigger;
import petrolpark.mc.destroy.util.DestroyReloadListener;

/**
 * Horizontal-facing decorative block that registers itself as an element on a 2D "periodic table"
 * grid. Placing one element block next to other correctly-positioned element blocks (within
 * {@code relative(...)} offsets) forms a full periodic-table structure and awards the
 * {@link DestroyAdvancementTrigger#PERIODIC_TABLE} advancement to the placing player.
 *
 * <p>The {@link ELEMENTS} set is populated by the reload listener {@link Listener} which scans
 * {@code data/<ns>/destroy_compat/periodic_table_blocks.json} — a flat map of
 * {@code "namespace:block_id": { "x": int, "y": int }} entries. Empty-set state (no JSON data)
 * is handled gracefully — block placement simply skips the grid-completion check.</p>
*/
@EventBusSubscriber(modid = Destroy.MOD_ID)
public class PeriodicTableBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<PeriodicTableBlock> CODEC = simpleCodec(PeriodicTableBlock::new);

    public static Set<PeriodicTableEntry> ELEMENTS = new HashSet<>();

    public PeriodicTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PeriodicTableBlock> codec() {
        return CODEC;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    public static BlockPos relative(Block block, Block blockToPlace, Direction tableFacing) {
        return relative(getXY(block), getXY(blockToPlace), tableFacing);
    }

    public static BlockPos relative(int[] thisPos, int[] thatPos, Direction tableFacing) {
        int horizontalOffset = (thatPos[0] - thisPos[0]) * (tableFacing.getAxisDirection() == AxisDirection.NEGATIVE ? -1 : 1);
        return new BlockPos(tableFacing.getAxis() == Axis.X ? 0 : horizontalOffset, thisPos[1] - thatPos[1], tableFacing.getAxis() == Axis.Z ? 0 : -horizontalOffset);
    }

    public static boolean isPeriodicTableBlock(BlockState state) {
        return isPeriodicTableBlock(state.getBlock());
    }

    public static boolean isPeriodicTableBlock(Block block) {
        return ELEMENTS.stream().anyMatch(entry -> entry.blocks.contains(block));
    }

    public static int[] getXY(Block block) {
        Optional<PeriodicTableEntry> entry = ELEMENTS.stream().filter(e -> e.blocks.contains(block)).findFirst();
        if (entry.isPresent()) return new int[]{entry.get().x, entry.get().y};
        return new int[2];
    }

    public static record PeriodicTableEntry(List<Block> blocks, int x, int y) {}

    /**
 * {@link DestroyReloadListener}-based JSON scanner. Reads
 * {@code data/<ns>/destroy_compat/periodic_table_blocks.json} entries shaped as
 * {@code "destroy:xyz_periodic_table_block": { "x": N, "y": M }} and registers each valid
 * block ID into {@link ELEMENTS}. Invalid/missing block IDs raise {@link IllegalStateException}.
*/
    public static class Listener extends DestroyReloadListener {

        public Listener() {
            super();
        }

        @Override
        public String getPath() {
            return "destroy_compat/periodic_table_blocks";
        }

        @Override
        public void beforeReload() {
            ELEMENTS.clear();
        }

        @Override
        public void forEachNameSpaceJsonFile(JsonObject jsonObject) {
            jsonObject.entrySet().forEach(entry -> {
                Optional<? extends Holder<Block>> blockOptional = BuiltInRegistries.BLOCK.getHolder(ResourceKey.create(Registries.BLOCK, ResourceLocation.parse(entry.getKey())));
                if (blockOptional.isEmpty()) {
                    // element-specific periodic_table_blocks are deferred (see DestroyBlocks.PERIODIC_TABLE
                    // comment). JSON references unregistered IDs during incomplete migration — warn + skip
                    // instead of crashing so the data-pack can list future blocks without gating world load.
                    Destroy.LOGGER.warn("Skipping unregistered periodic table block ID: {}", entry.getKey());
                    return;
                }
                JsonObject pos = entry.getValue().getAsJsonObject();
                int x = pos.get("x").getAsInt();
                int y = pos.get("y").getAsInt();
                boolean found = false;
                Block block = blockOptional.get().value();

                for (PeriodicTableEntry element : ELEMENTS) {
                    if (element.x == x && element.y == y) {
                        element.blocks.add(block);
                        found = true;
                        break;
                    }
                }

                if (!found) ELEMENTS.add(new PeriodicTableEntry(new ArrayList<>(List.of(block)), x, y));
            });
        }

        /** Client-side handler is currently a degraded no-op
 * (see {@link petrolpark.mc.destroy.client.DestroyPonderScenes#refreshPeriodicTableBlockScenes}
 * for architectural rationale — 1.21 Ponder SceneRegistryAccess does not expose a mutable
 * registry). Packet dispatch preserved for a future Mixin-based unblock path.
*/
        @Override
        public void afterReload() {
            net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(
                RefreshPeriodicTablePonderSceneS2CPacket.INSTANCE);
        }
    }

    /**
 * Reward the Player with an advancement for assembling a full periodic table.
*/
    @SubscribeEvent
    public static void onEntityPlace(EntityPlaceEvent event) {
        // getEntity() is nullable: blocks placed without a living placer (dispensers, and other
        // mods' fabricated placement events — e.g. Ballistix's explosion overwrite check) fire this
        // with a null entity. Only Players earn the advancement, so gate on that first.
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        BlockState state = event.getPlacedBlock();
        if (!PeriodicTableBlock.isPeriodicTableBlock(state)) return;
        Level level = serverPlayer.level();

        int[] thisPos = PeriodicTableBlock.getXY(state.getBlock());
        for (Direction direction : Iterate.horizontalDirections) {
            boolean allPresent = true;
            checkEachBlock: for (PeriodicTableEntry entry : PeriodicTableBlock.ELEMENTS) {
                if (!entry.blocks().contains(level.getBlockState(event.getPos().offset(PeriodicTableBlock.relative(thisPos, new int[]{entry.x(), entry.y()}, direction))).getBlock())) {
                    allPresent = false;
                    break checkEachBlock;
                }
            }
            if (allPresent) {
                DestroyAdvancementTrigger.PERIODIC_TABLE.award(level, serverPlayer);
                return;
            }
        }
    }
}
