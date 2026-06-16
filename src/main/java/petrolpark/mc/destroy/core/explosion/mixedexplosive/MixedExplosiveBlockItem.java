package petrolpark.mc.destroy.core.explosion.mixedexplosive;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Block;

import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties.ExplosivePropertyCondition;

/**
 * BlockItem form of {@link MixedExplosiveBlock} that carries the {@link MixedExplosiveInventory}
 * payload + dye color across pick-block / creative-dup / shulker-box storage via DataComponents.
 *
 * <p>DestroyAllConfigs.SERVER.blocks.customExplosiveMixSize hardcoded to 9 (matches the BE and
 * Entity defaults) pending config audit.</p>
*/
public class MixedExplosiveBlockItem extends BlockItem implements IMixedExplosiveItem {

    public MixedExplosiveBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    // ---------- Dye color via 1.21 DataComponents.DYED_COLOR ----------

    /**
 * Read dye color from the stack's {@link DataComponents#DYED_COLOR} component.
*/
    public int getColor(ItemStack stack) {
        return DyedItemColor.getOrDefault(stack, 0xFFFFFF);
    }

    /**
 * Write dye color to the stack's {@link DataComponents#DYED_COLOR} component.
*/
    public void setColor(ItemStack stack, int color) {
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, true));
    }

    // ---------- IMixedExplosiveItem interface contract ----------

    @Override
    public int getExplosiveInventorySize() {
        // Placeholder: DestroyAllConfigs.SERVER.blocks.customExplosiveMixSize pending config audit.
        // Matches MixedExplosiveBlockEntity.createInv() + MixedExplosiveEntity default.
        return 9;
    }

    @Override
    public ExplosivePropertyCondition[] getApplicableExplosionConditions() {
        return MixedExplosiveBlockEntity.EXPLOSIVE_PROPERTY_CONDITIONS;
    }

    /**
 * Visual "example" stack used by JEI (ObliterationCategory catalyst slot) — a pre-dyed +
 * pre-named CUSTOM_EXPLOSIVE_MIX stack.
*/
    public static ItemStack getExampleItemStack() {
        ItemStack stack = DestroyBlocks.CUSTOM_EXPLOSIVE_MIX.asStack();
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x85B09A, true));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("MIX"));
        return stack;
    }
}
