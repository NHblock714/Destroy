package petrolpark.mc.destroy.core.chemistry;

import java.util.HashSet;
import java.util.Set;

import com.petrolpark.client.ponder.instruction.HighlightTagInstruction;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.instruction.FadeOutOfSceneInstruction;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import petrolpark.mc.destroy.DestroyItems;
import petrolpark.mc.destroy.client.DestroyParticleTypes;
import petrolpark.mc.destroy.client.DestroyPonderTags;
import petrolpark.mc.destroy.content.processing.ProcessingPonderScenes;
import petrolpark.mc.destroy.content.processing.distillation.BubbleCapBlockEntity;
import petrolpark.mc.destroy.content.product.periodictable.PeriodicTableBlock;
import petrolpark.mc.destroy.content.product.periodictable.PeriodicTableBlock.PeriodicTableEntry;
import petrolpark.mc.destroy.core.chemistry.vat.VatSideBlockEntity;
import petrolpark.mc.destroy.core.chemistry.vat.ponder.DrainVatPonderInstruction;
import petrolpark.mc.destroy.core.chemistry.vat.ponder.SetVatPressurePonderInstruction;
import petrolpark.mc.destroy.core.chemistry.vat.ponder.SetVatSideTypePonderInstruction;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlock;

import petrolpark.mc.destroy.core.chemistry.vat.observation.colorimeter.ColorimeterBlock;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import petrolpark.mc.destroy.core.chemistry.vat.ponder.ThermometerInstruction;
import petrolpark.mc.destroy.core.chemistry.vat.ponder.ThermometerInstruction.ThermometerElement;
import petrolpark.mc.destroy.core.explosion.ExplodePonderInstruction;
import petrolpark.mc.destroy.core.fluid.gasparticle.GasParticleData;

import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/**
 *
 * <p>The {@code scenePending} helper is retained as a reusable placeholder (same approach as
 * {@link petrolpark.mc.destroy.content.processing.ProcessingPonderScenes#scenePending} +
 * {@link petrolpark.mc.destroy.core.pollution.PollutionPonderScenes#scenePending}); the
 * scene-registry wiring in {@link petrolpark.mc.destroy.client.DestroyPonderScenes} is
 * independent of the individual scene bodies.</p>
*/
public class ChemistryPonderScenes {

    /**
 * Placeholder scene. Same pattern as
 * {@link petrolpark.mc.destroy.content.processing.ProcessingPonderScenes#scenePending}.
*/
    public static void scenePending(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("chemistry.scene_pending", "This scene is not yet implemented.");
        scene.configureBasePlate(0, 0, 3);
        scene.showBasePlate();
        scene.overlay().showText(200)
            .text("This scene is being ported. Check back in a future Destroy update.")
            .independent();
        scene.idle(220);
        scene.markAsFinished();
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // Chemistry scenes
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    /**
 * Reactions scene. Basin + MechanicalMixer processing narrative. Registered in
 * {@link petrolpark.mc.destroy.client.DestroyPonderScenes} on {@code AllBlocks.BASIN}
 * component with {@code DestroyPonderTags.CHEMISTRY} tag.
*/
    public static void reactions(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("reactions", "This text is defined in a language file.");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        scene.world().showSection(util.select().fromTo(2, 1, 2, 4, 3, 4), Direction.DOWN);
        scene.idle(20);
        scene.world().showSection(util.select().position(0, 0, 5), Direction.NORTH);
        scene.idle(5);
        for (int y = 1; y <= 3; y++) {
            scene.world().showSection(util.select().position(0, y, 4), Direction.DOWN);
            scene.idle(5);
        }
        for (int z = 3; z >= 1; z--) {
            scene.world().showSection(util.select().position(0, 3, z), Direction.DOWN);
            scene.idle(5);
        }
        scene.idle(10);
        scene.world().showSection(util.select().position(0, 1, 1), Direction.SOUTH);
        scene.idle(10);

        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(util.grid().at(2, 2, 3), Direction.WEST));
        scene.idle(20);
        scene.world().modifyBlockEntity(util.grid().at(0, 3, 1), MechanicalMixerBlockEntity.class, MechanicalMixerBlockEntity::startProcessingBasin);
        scene.idle(80);
        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .attachKeyFrame()
            .independent();
        scene.idle(100);
        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .colored(PonderPalette.GREEN)
            .independent();
        scene.idle(100);
    }

    /**
 * Bunsen burner scene.
*/
    public static void bunsenBurner(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("bunsen_burner", "This text is defined in a language file.");
    }

    /** Complex
 * multi-Vat tutorial narrative demonstrating Vat shape/size constraints:
 * <ol>
 * <li>**Vat 1**: Reveal 5×5×5 complete frame floor → walls → roof (stepped layer reveal).
 * Show RED/GREEN outline cycle on controller location (missing-space warning →
 * resolved). RED text warning about east-side "hole" + HighlightTagInstruction showing
 * VAT_SIDE_BLOCKS tag index entries. Slide copper wall in from east, hide; slide iron
 * wall in further (2 blocks out) — demonstrating material options.</li>
 * <li>**Vat 2 (bad case)**: Reveal small 3×3×3 vat; stays 5 blocks down. Text hint "too
 * small"; hide.</li>
 * <li>**Vat 3 (bad case)**: Reveal massive 7×7×7 vat (offset 8 down); text hint "too
 * large"; hide.</li>
 * <li>**Vat 4 (exploded view)**: Reveal 5 sub-sections (north/east/south/west/remainder)
 * independently at −16 offset; show "pop out" animation (each wall drifts out by 2
 * blocks in its facing direction over 10 ticks), then "slide back" animation restoring
 * assembly. Text hint shows non-cubic (tall rectangular) vat is valid.</li>
 * </ol>
 *
 * <ul>
 * <li>Pure Ponder API (showSection / showOutline / showIndependentSection / moveSection /
 * hideSection / hideIndependentSection) preserved.</li>
 * <li>{@code Selection} composite operations (fromTo / position / substract / add / copy)
 * unchanged.</li>
 * <li>{@link HighlightTagInstruction} at {@code com.petrolpark.client.ponder.instruction.*}
 * — petrolpark library 1.21 preserves the Ponder instruction package path 1:1 (unlike
 * other petrolpark-lib classes using `core/` extra layer).</li>
 * <li>{@link DestroyPonderTags#VAT_SIDE_BLOCKS} constant available.</li>
 * <li>{@link PonderPalette#RED} / {@link PonderPalette#GREEN}.</li>
 * </ul>
*/
    public static void vatConstruction(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("vat.construction", "This text is defined in a language file.");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.5f);
        scene.showBasePlate();

        // Vat 1
        BlockPos vat1ControllerPos = util.grid().at(4, 2, 2);
        Selection vat1Controller = util.select().position(vat1ControllerPos);
        Selection vat1 = util.select().fromTo(2, 1, 2, 6, 5, 6);
        Selection vat1Floor = util.select().fromTo(2, 1, 2, 6, 1, 6);
        Selection vat1South = util.select().fromTo(2, 2, 6, 6, 4, 6);
        Selection vat1North = util.select().fromTo(2, 2, 2, 6, 4, 2).substract(vat1Controller);
        Selection vat1East = util.select().fromTo(6, 2, 3, 6, 4, 5);
        Selection vat1West = util.select().fromTo(2, 2, 3, 2, 4, 5);
        BlockPos vat1WestCenter = util.grid().at(2, 3, 4);
        Selection vat1Roof = util.select().fromTo(2, 5, 2, 6, 5, 6);
        Selection vat1CopperWall = util.select().fromTo(1, 2, 3, 1, 4, 5);
        Selection vat1IronWall = util.select().fromTo(0, 2, 3, 0, 4, 5);

        // Vat 2
        Selection vat2 = util.select().fromTo(3, 6, 3, 5, 8, 5);

        // Vat 3
        Selection vat3 = util.select().fromTo(1, 9, 1, 7, 15, 7);

        // Vat 4
        Selection vat4 = util.select().fromTo(3, 17, 1, 5, 21, 7);
        Selection vat4West = util.select().fromTo(3, 17, 1, 3, 21, 7).substract(util.select().fromTo(3, 18, 2, 3, 20, 6));
        Selection vat4East = util.select().fromTo(5, 17, 1, 5, 21, 7).substract(util.select().fromTo(5, 18, 2, 5, 20, 6));
        Selection vat4North = util.select().position(4, 17, 1).add(util.select().position(4, 21, 1));
        Selection vat4South = util.select().position(4, 17, 7).add(util.select().position(4, 21, 7));
        Selection vat4Remainder = vat4.copy().substract(vat4West).substract(vat4East).substract(vat4North).substract(vat4South);

        scene.idle(10);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.idle(20);
        scene.world().showSection(vat1Floor, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(vat1South, Direction.NORTH);
        scene.idle(10);
        scene.world().showSection(vat1East, Direction.WEST);
        scene.idle(10);
        scene.world().showSection(vat1West, Direction.EAST);
        scene.idle(10);
        scene.world().showSection(vat1North, Direction.SOUTH);
        scene.idle(10);
        scene.world().showSection(vat1Roof, Direction.DOWN);
        scene.idle(50);

        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(vat1ControllerPos, Direction.WEST));
        scene.overlay().showOutline(PonderPalette.RED, "missing_space", vat1Controller, 40);
        scene.idle(50);
        scene.world().showSection(vat1Controller, Direction.SOUTH);
        scene.idle(5);
        scene.overlay().showOutline(PonderPalette.GREEN, "missing_space", vat1Controller, 40);
        scene.idle(60);

        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .colored(PonderPalette.RED)
            .pointAt(util.vector().blockSurface(vat1WestCenter, Direction.WEST))
            .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
            .text("This text is defined in a language file");
        scene.addInstruction(new HighlightTagInstruction(DestroyPonderTags.VAT_SIDE_BLOCKS, 80));
        scene.idle(100);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.world().hideSection(vat1West, Direction.WEST);
        scene.idle(20);
        ElementLink<WorldSectionElement> copperWallLink = scene.world().showIndependentSection(vat1CopperWall, Direction.EAST);
        scene.world().moveSection(copperWallLink, new Vec3(1, 0, 0), 0);
        scene.idle(40);
        scene.world().hideIndependentSection(copperWallLink, Direction.WEST);
        scene.idle(20);
        ElementLink<WorldSectionElement> ironWallLink = scene.world().showIndependentSection(vat1IronWall, Direction.EAST);
        scene.world().moveSection(ironWallLink, new Vec3(2, 0, 0), 0);
        scene.idle(60);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.idle(20);
        scene.world().hideSection(vat1.substract(vat1West), Direction.UP);
        scene.world().hideIndependentSection(ironWallLink, Direction.UP);
        scene.idle(20);
        ElementLink<WorldSectionElement> vat2Link = scene.world().showIndependentSection(vat2, Direction.DOWN);
        scene.world().moveSection(vat2Link, new Vec3(0, -5, 0), 0);
        scene.idle(60);
        scene.world().hideIndependentSection(vat2Link, Direction.UP);
        scene.idle(20);
        scene.overlay().showText(40)
            .text("This text is defined in a language file.");
        ElementLink<WorldSectionElement> vat3Link = scene.world().showIndependentSection(vat3, Direction.DOWN);
        scene.world().moveSection(vat3Link, new Vec3(0, -8, 0), 0);
        scene.idle(60);

        scene.world().hideIndependentSection(vat3Link, Direction.UP);
        scene.overlay().showText(60)
            .text("This text is defined in a language file.");
        scene.idle(20);
        ElementLink<WorldSectionElement> vat4NorthLink = scene.world().showIndependentSection(vat4North, Direction.DOWN);
        ElementLink<WorldSectionElement> vat4EastLink = scene.world().showIndependentSection(vat4East, Direction.DOWN);
        ElementLink<WorldSectionElement> vat4SouthLink = scene.world().showIndependentSection(vat4South, Direction.DOWN);
        ElementLink<WorldSectionElement> vat4WestLink = scene.world().showIndependentSection(vat4West, Direction.DOWN);
        ElementLink<WorldSectionElement> vat4RemainderLink = scene.world().showIndependentSection(vat4Remainder, Direction.DOWN);
        scene.world().moveSection(vat4NorthLink, new Vec3(0, -16, 0), 0);
        scene.world().moveSection(vat4EastLink, new Vec3(0, -16, 0), 0);
        scene.world().moveSection(vat4SouthLink, new Vec3(0, -16, 0), 0);
        scene.world().moveSection(vat4WestLink, new Vec3(0, -16, 0), 0);
        scene.world().moveSection(vat4RemainderLink, new Vec3(0, -16, 0), 0);
        scene.idle(60);
        scene.overlay().showText(80)
            .pointAt(util.vector().topOf(util.grid().at(4, 5, 4)))
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().topOf(util.grid().at(3, 5, 4)))
            .attachKeyFrame();
        scene.idle(20);
        scene.world().moveSection(vat4NorthLink, new Vec3(0, 0, -2), 10);
        scene.world().moveSection(vat4EastLink, new Vec3(2, 0, 0), 10);
        scene.world().moveSection(vat4SouthLink, new Vec3(0, 0, 2), 10);
        scene.world().moveSection(vat4WestLink, new Vec3(-2, 0, 0), 10);
        scene.idle(60);
        scene.world().moveSection(vat4NorthLink, new Vec3(0, 0, 2), 10);
        scene.world().moveSection(vat4EastLink, new Vec3(-2, 0, 0), 10);
        scene.world().moveSection(vat4SouthLink, new Vec3(0, 0, -2), 10);
        scene.world().moveSection(vat4WestLink, new Vec3(2, 0, 0), 10);
        scene.idle(20);

        scene.markAsFinished();
    }

    /** 7-phase narrative demonstrating fluid I/O
 * through Vat side cells + invalid pipe configurations:
 * <ol>
 * <li>Reveal 2 vats + pipe network connecting them; toggle side 1 from NORMAL → PIPE
 * (via {@link SetVatSideTypePonderInstruction}); propagate pipe change.</li>
 * <li>RED warning about invalid top output (vat walls must be PIPE to route through).</li>
 * <li>Show invalid pipe config: reveal upper pipe section → RED outlines on input + output
 * (wrong side types); hide after warning.</li>
 * <li>Lava tank scenario: reveal lava tank + pump + kinetics + lava pipe; switch side 2
 * to PIPE; RED outlines on lava pipe + tank (fluid compat warning); hide.</li>
 * <li>Highlight second vat (small vat2) with GREEN outline; hide vat2 + reveal external
 * fluid tank; RED warning about external tank; hide.</li>
 * <li>Basin scenario: reveal basin as alternative output target; text prompts.</li>
 * <li>Trigger {@link DrainVatPonderInstruction} to drain 3500mB from vat; propagate pipe
 * change. Remove smart pipe + re-reveal as independent section moved to new position;
 * RED warning about smart-pipe direction mismatch.</li>
 * </ol>
 *
 * <ul>
 * <li>{@link SetVatSideTypePonderInstruction} — 4 invocations exercising all
 * DisplayType transitions (NORMAL, PIPE, back-and-forth).</li>
 * <li>{@link DrainVatPonderInstruction} — the drain animation is visual-only
 * (propagatePipeChange triggers the fluid-flow visualization; the actual fluid-amount
 * drain is not applied here).</li>
 * <li>{@code propagatePipeChange / showIndependentSection / moveSection /
 * hideIndependentSection / showOutline} preserved.</li>
 * <li>{@link Items#LAVA_BUCKET} + {@link Blocks#AIR} vanilla.</li>
 * </ul>
*/
    public static void vatFluids(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("vat.fluids", "This text is defined in a language file.");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.5f);
        scene.showBasePlate();

        BlockPos pipeConnection1 = util.grid().at(6, 2, 4);
        BlockPos pipeConnection2 = util.grid().at(5, 3, 4);
        Selection vat1 = util.select().fromTo(4, 1, 4, 7, 4, 7);
        Selection vat2 = util.select().fromTo(0, 1, 1, 2, 3, 3);
        Selection pipe = util.select().fromTo(3, 2, 2, 6, 2, 3);
        BlockPos pump1 = util.grid().at(6, 2, 3);
        Selection invalidPipe = util.select().fromTo(3, 3, 2, 6, 4, 3).substract(util.select().fromTo(5, 3, 2, 5, 3, 3));
        BlockPos lavaTank = util.grid().at(6, 1, 0);
        Selection lavaKinetics = util.select().fromTo(7, 1, 0, 9, 1, 0).add(util.select().fromTo(6, 2, 0, 7, 3, 0));
        Selection lavaPipe = util.select().fromTo(5, 3, 0, 5, 3, 3);
        Selection tank = util.select().fromTo(0, 4, 1, 2, 6, 3);
        BlockPos basin = util.grid().at(1, 1, 6);
        BlockPos smartPipe = util.grid().at(4, 2, 2);

        scene.addInstruction(new SetVatSideTypePonderInstruction(pipeConnection1, VatSideBlockEntity.DisplayType.NORMAL));
        scene.addInstruction(new SetVatSideTypePonderInstruction(pipeConnection2, VatSideBlockEntity.DisplayType.NORMAL));

        scene.idle(10);
        scene.world().showSection(vat1, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(vat2, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(9, 0, 1), Direction.WEST);
        scene.idle(5);
        scene.world().showSection(util.select().position(9, 1, 2), Direction.WEST);
        scene.idle(5);
        scene.world().showSection(util.select().position(8, 1, 3), Direction.NORTH);
        scene.idle(5);
        scene.world().showSection(util.select().position(7, 2, 3), Direction.NORTH);
        scene.idle(10);
        scene.world().showSection(pipe, Direction.DOWN);
        scene.idle(10);
        scene.addInstruction(new SetVatSideTypePonderInstruction(pipeConnection1, VatSideBlockEntity.DisplayType.PIPE));
        scene.idle(10);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().topOf(pump1));
        scene.world().propagatePipeChange(pump1);
        scene.idle(120);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .colored(PonderPalette.RED)
            .pointAt(util.vector().topOf(5, 4, 5));
        scene.idle(120);

        scene.world().showSection(invalidPipe, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().topOf(6, 4, 3));
        scene.idle(20);
        scene.overlay().showOutline(PonderPalette.RED, "invalid output", util.select().position(6, 4, 4), 80);
        scene.overlay().showOutline(PonderPalette.RED, "invalid input", util.select().position(2, 3, 2), 80);
        scene.idle(100);
        scene.world().hideSection(invalidPipe, Direction.UP);
        scene.idle(20);

        scene.overlay().showText(200)
            .text("This text is defined in a language file.")
            .independent()
            .attachKeyFrame()
            .colored(PonderPalette.RED);
        scene.idle(20);
        scene.world().showSection(util.select().position(lavaTank), Direction.DOWN);
        scene.idle(20);
        scene.overlay().showControls(util.vector().blockSurface(lavaTank, Direction.NORTH), Pointing.RIGHT, 40).withItem(new ItemStack(Items.LAVA_BUCKET));
        scene.idle(60);
        scene.world().showSection(lavaKinetics, Direction.WEST);
        scene.idle(10);
        scene.world().showSection(lavaPipe, Direction.DOWN);
        scene.idle(10);
        scene.addInstruction(new SetVatSideTypePonderInstruction(pipeConnection2, VatSideBlockEntity.DisplayType.PIPE));
        scene.idle(20);
        scene.overlay().showOutline(PonderPalette.RED, "lava pipe", lavaPipe, 60);
        scene.overlay().showOutline(PonderPalette.RED, "lava tank", util.select().position(lavaTank), 60);
        scene.idle(80);
        scene.addInstruction(new SetVatSideTypePonderInstruction(pipeConnection2, VatSideBlockEntity.DisplayType.NORMAL));
        scene.world().hideSection(lavaPipe, Direction.UP);
        scene.world().hideSection(lavaKinetics, Direction.UP);
        scene.world().hideSection(util.select().position(lavaTank), Direction.UP);
        scene.idle(20);

        scene.overlay().showText(120)
            .text("This text is defined in a language file.")
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(util.grid().at(0, 2, 2), Direction.WEST));
        scene.overlay().showOutline(PonderPalette.GREEN, "vat2", vat2, 40);
        scene.idle(40);
        scene.world().hideSection(vat2, Direction.UP);
        scene.idle(20);
        ElementLink<WorldSectionElement> tankLink = scene.world().showIndependentSection(tank, Direction.DOWN);
        scene.world().moveSection(tankLink, util.vector().of(0d, -3d, 0d), 0);
        scene.idle(20);
        scene.overlay().showOutline(PonderPalette.RED, "tank", vat2, 40);
        scene.idle(60);
        scene.world().hideIndependentSection(tankLink, Direction.UP);
        scene.idle(20);

        scene.world().showSection(util.select().position(basin), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(basin, Direction.WEST))
            .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().topOf(5, 4, 6));
        scene.idle(100);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(util.grid().at(4, 3, 6), Direction.WEST));
        scene.idle(40);
        scene.addInstruction(new DrainVatPonderInstruction(util.grid().at(5, 2, 4), 3500));
        scene.idle(20);
        scene.world().propagatePipeChange(pump1);
        scene.idle(40);

        scene.world().setBlock(smartPipe, Blocks.AIR.defaultBlockState(), true);
        scene.idle(10);
        ElementLink<WorldSectionElement> smartPipeLink = scene.world().showIndependentSection(util.select().position(4, 2, 1), Direction.SOUTH);
        scene.world().moveSection(smartPipeLink, util.vector().of(0d, 0d, 1d), 0);
        scene.world().propagatePipeChange(pump1);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.RED, "smart pipe", util.select().position(smartPipe), 80);
        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .colored(PonderPalette.RED)
            .pointAt(util.vector().blockSurface(smartPipe, Direction.UP))
            .attachKeyFrame();
        scene.idle(100);

        // smartPipeLink is intentionally unused post-creation — scene graph holds the reference
        // via showIndependentSection + moveSection; local var retained for readability.
        assert smartPipeLink != null;

        scene.markAsFinished();
    }

    /** Narrative:
 * <ol>
 * <li>Reveal Vat frame + overhead funnel. Show item drop prompt pointing at funnel.</li>
 * <li>{@link AllItems#PULP Paper pulp} item drop into the vat via top funnel → kill
 * item entity (Vat absorbs it).</li>
 * <li>Reveal side funnel (Vat output path). Flap funnel + spawn
 * {@link DestroyItems#NITROCELLULOSE Nitrocellulose} output item entity.</li>
 * <li>Reveal bottom funnel + RED outline warning (reaction products do not exit via
 * the bottom face — requires side/top exit).</li>
 * </ol>
*/
    public static void vatItems(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("vat.items", "This text is defined in a language file.");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        scene.idle(5);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 3, 3, 3), Direction.DOWN);
        scene.idle(10);

        scene.world().showSection(util.select().position(2, 4, 2), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(120)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(util.grid().at(2, 4, 2), Direction.WEST));
        scene.idle(20);
        ElementLink<EntityElement> iodine = scene.world().createItemEntity(util.vector().centerOf(2, 6, 2), Vec3.ZERO, AllItems.PULP.asStack());
        scene.idle(20);
        scene.world().modifyEntity(iodine, Entity::kill);
        scene.idle(30);
        scene.world().showSection(util.select().position(0, 2, 2), Direction.EAST);
        scene.idle(30);
        scene.world().flapFunnel(util.grid().at(0, 2, 2), true);
        scene.world().createItemEntity(util.vector().centerOf(0, 2, 2), Vec3.ZERO, DestroyItems.NITROCELLULOSE.asStack());
        scene.idle(60);

        scene.world().showSection(util.select().position(1, 2, 0), Direction.SOUTH);
        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(util.grid().at(1, 2, 0), Direction.UP));
        scene.overlay().showOutline(PonderPalette.RED, "funnel", util.select().position(1, 2, 0), 80);
        scene.idle(100);

        scene.markAsFinished();
    }

    /** Complex 6-phase narrative demonstrating
 * Vat temperature instrumentation + heat source integration:
 * <ol>
 * <li>Reveal Vat frame offset down by 2 (setup animation); wrench-cycle one side to
 * THERMOMETER display via {@link SetVatSideTypePonderInstruction}.</li>
 * <li>Spawn side {@link ThermometerElement vatThermo} (0.75 init) + ambient thermometer
 * at topOf(0, 0, 1) (0.25 long-lifetime ref); chase vatThermo to 0.25 (cooling to
 * room temp — demonstrates material conductance).</li>
 * <li>Hide vat face; reveal copper-clad side (higher thermal conductivity); chase
 * new vatThermo to 0.25 faster (chaseSpeed 0.1f vs 0.0025f — conductance matters).</li>
 * <li>Restore original side; move whole vat up 1; reveal Blaze Burner below; slide burner
 * into vat bottom + chase new vatThermo to 0.5.</li>
 * <li>Remove burner + chase down; reveal 2nd burner (SEETHING, showSectionAndMerge to
 * attach to existing burnerLink); chase vatThermo to 1.0 (super-heating).</li>
 * <li>Hide burner; move vat up 1 more; reveal Cooler from side; chase vatThermo to 0
 *.</li>
 * </ol>
 *
 * <ul>
 * <li>{@link ThermometerInstruction} drives 5 gauges — each {@code add(...)}
 * call instantiates a new ThermometerElement with its own lifetime + initial value +
 * screen position.</li>
 * <li>{@link SetVatSideTypePonderInstruction} + DisplayType.THERMOMETER.</li>
 * <li>{@code showIndependentSection / moveSection / hideIndependentSection /
 * showSectionAndMerge} Ponder API preserved.</li>
 * <li>{@link AllItems#WRENCH} + {@link Pointing#RIGHT}.</li>
 * <li>{@link IVatHeaterBlock} — not directly instantiated in this Ponder scene
 * (scene uses visual burner reveal + ThermometerInstruction.chase for animation; full
 * heating simulation would need Vat BE tick integration).</li>
 * </ul>
*/
    public static void vatTemperature(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("vat.temperature", "This text is defined in a language file.");
        scene.configureBasePlate(0, 1, 5);
        scene.showBasePlate();
        scene.scaleSceneView(0.7f);

        Selection vatFace = util.select().fromTo(1, 4, 3, 1, 6, 4);
        Selection vat = util.select().fromTo(1, 3, 1, 4, 7, 5).substract(vatFace);
        Vec3 vatThermoLoc = util.vector().of(3.5d, 4d, 1.875d);

        scene.idle(10);
        ElementLink<WorldSectionElement> vatLink = scene.world().showIndependentSection(vat, Direction.DOWN);
        ElementLink<WorldSectionElement> vatFaceLink = scene.world().showIndependentSection(vatFace, Direction.DOWN);
        scene.world().moveSection(vatLink, util.vector().of(0d, -2d, 0d), 0);
        scene.world().moveSection(vatFaceLink, util.vector().of(0d, -2d, 0d), 0);
        scene.idle(10);

        BlockPos wrenchedSideApparent = util.grid().at(3, 3, 2);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(wrenchedSideApparent, Direction.NORTH));
        scene.idle(40);
        scene.overlay().showControls(util.vector().blockSurface(wrenchedSideApparent, Direction.NORTH), Pointing.RIGHT, 40).withItem(AllItems.WRENCH.asStack());
        scene.idle(20);
        scene.addInstruction(new SetVatSideTypePonderInstruction(util.grid().at(3, 5, 2), VatSideBlockEntity.DisplayType.THERMOMETER));
        scene.idle(20);
        ThermometerElement vatThermo = ThermometerInstruction.add(scene, vatThermoLoc, 200, 0.75f);
        scene.idle(40);

        ThermometerInstruction.add(scene, util.vector().topOf(0, 0, 1), 840, 0.25f);
        scene.idle(20);
        scene.overlay().showText(120)
            .text("This text is defined in a language file.")
            .attachKeyFrame()
            .independent();
        scene.idle(20);
        scene.addInstruction(ThermometerInstruction.chase(vatThermo, 0.25f, 0.0025f));
        scene.idle(160);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .attachKeyFrame()
            .independent();
        vatThermo = ThermometerInstruction.add(scene, vatThermoLoc, 100, 0.75f);
        scene.idle(20);
        scene.world().hideIndependentSection(vatFaceLink, Direction.WEST);
        scene.idle(20);
        ElementLink<WorldSectionElement> copperLink = scene.world().showIndependentSection(util.select().fromTo(0, 4, 3, 0, 6, 4), Direction.EAST);
        scene.world().moveSection(copperLink, util.vector().of(1d, -2d, 0d), 0);
        scene.idle(20);
        scene.addInstruction(ThermometerInstruction.chase(vatThermo, 0.25f, 0.1f));
        scene.idle(60);

        scene.world().hideIndependentSection(copperLink, Direction.WEST);
        scene.idle(20);
        vatFaceLink = scene.world().showIndependentSection(vatFace, Direction.EAST);
        scene.world().moveSection(vatFaceLink, util.vector().of(0d, -2d, 0d), 0);
        scene.idle(20);

        scene.world().moveSection(vatLink, util.vector().of(0d, 1d, 0d), 15);
        scene.world().moveSection(vatFaceLink, util.vector().of(0d, 1d, 0d), 15);
        scene.idle(15);
        vatThermo = ThermometerInstruction.add(scene, vatThermoLoc.add(0d, 1d, 0d), 315, 0.25f);
        scene.idle(15);
        BlockPos burner1 = util.grid().at(2, 1, 1);
        ElementLink<WorldSectionElement> burnerLink = scene.world().showIndependentSection(util.select().position(burner1), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(burner1, Direction.WEST));
        scene.idle(20);
        scene.world().moveSection(burnerLink, util.vector().of(0d, 0d, 2d), 20);
        scene.idle(40);
        scene.addInstruction(ThermometerInstruction.chase(vatThermo, 0.5f, 0.01f));
        scene.idle(60);

        scene.world().moveSection(burnerLink, util.vector().of(0d, 0d, -2d), 20);
        scene.addInstruction(ThermometerInstruction.chase(vatThermo, 0.25f, 0.0025f));
        scene.idle(20);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .independent()
            .attachKeyFrame();
        scene.idle(20);
        scene.world().showSectionAndMerge(util.select().fromTo(2, 1, 0, 3, 1, 1).substract(util.select().position(burner1)), Direction.DOWN, burnerLink);
        scene.idle(20);
        scene.world().moveSection(burnerLink, util.vector().of(0d, 0d, 3d), 20);
        scene.idle(20);
        scene.addInstruction(ThermometerInstruction.chase(vatThermo, 1f, 0.01f));
        scene.idle(60);

        scene.world().hideIndependentSection(burnerLink, Direction.NORTH);
        scene.idle(20);
        scene.world().moveSection(vatLink, util.vector().of(0d, 1d, 0d), 15);
        scene.world().moveSection(vatFaceLink, util.vector().of(0d, 1d, 0d), 15);
        scene.idle(15);
        vatThermo = ThermometerInstruction.add(scene, vatThermoLoc.add(0d, 2d, 0d), 135, 0.5f);
        scene.idle(15);
        scene.overlay().showText(120)
            .text("This text is defined in a language file.")
            .independent()
            .attachKeyFrame();
        scene.idle(20);
        ElementLink<WorldSectionElement> coolerLink = scene.world().showIndependentSection(util.select().fromTo(2, 1, 3, 5, 2, 3).add(util.select().position(5, 0, 3)), Direction.WEST);
        scene.world().moveSection(coolerLink, util.vector().of(0d, 0d, -2d), 0);
        scene.idle(20);
        scene.world().moveSection(coolerLink, util.vector().of(0d, 0d, 2d), 20);
        scene.idle(30);
        scene.addInstruction(ThermometerInstruction.chase(vatThermo, 0f, 0.01f));
        scene.idle(70);

        scene.markAsFinished();
    }

    /** Narrative:
 * <ol>
 * <li>Reveal Vat multi-block frame; hold two top sides NORMAL to establish baseline.</li>
 * <li>Demonstrate wrench-cycle: NORMAL → THERMOMETER → BAROMETER on one side via
 * {@link SetVatSideTypePonderInstruction}.</li>
 * <li>Animate pressure gauge through +200kPa / −200kPa / 0Pa transitions via
 * {@link SetVatPressurePonderInstruction} (real LerpedFloat chase on VatControllerBE).</li>
 * <li>Reveal feed apparatus (tank + pipe into vat); pressure climbs to 250kPa.</li>
 * <li>Open vat + reveal Blaze Burners → pressure 500kPa; hide burners, pressure drops.</li>
 * <li>Re-pipe; pressure climbs to 750kPa + GREEN success text.</li>
 * <li>Glass ceiling shatter → copper roof; pressure ramps to 1000kPa →
 * {@link ExplodePonderInstruction} 5-radius DESTROY explosion finale.</li>
 * </ol>
 *
 * <ul>
 * * <li>Vat Ponder instructions used:
 * {@link SetVatSideTypePonderInstruction} + {@link SetVatPressurePonderInstruction}
 * + {@link ExplodePonderInstruction}. {@code pressure.chase(...)} is
 * functional via the VatControllerBE pressure field.</li>
 * <li>Demonstrates 5 Vat Ponder instructions under real scene load.</li>
 * </ul>
*/
    public static void vatPressure(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("vat.pressure", "This text is defined in a language file.");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.7f);
        scene.showBasePlate();

        scene.addInstruction(new SetVatSideTypePonderInstruction(util.grid().at(3, 2, 4), VatSideBlockEntity.DisplayType.NORMAL));
        scene.addInstruction(new SetVatSideTypePonderInstruction(util.grid().at(3, 3, 5), VatSideBlockEntity.DisplayType.NORMAL));
        Selection r = util.select().fromTo(4, 4, 4, 5, 4, 5);
        ElementLink<WorldSectionElement> vat = scene.world().showIndependentSection(util.select().fromTo(3, 1, 3, 6, 4, 6).substract(r), Direction.DOWN);
        ElementLink<WorldSectionElement> roof = scene.world().showIndependentSection(r, Direction.DOWN);
        scene.idle(10);

        BlockPos barometer = util.grid().at(5, 2, 3);
        Vec3 face = util.vector().blockSurface(barometer, Direction.NORTH);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(face);
        scene.idle(20);
        scene.overlay().showControls(face, Pointing.RIGHT, 30).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(5);
        scene.addInstruction(new SetVatSideTypePonderInstruction(barometer, VatSideBlockEntity.DisplayType.THERMOMETER));
        scene.idle(35);
        scene.overlay().showControls(face, Pointing.RIGHT, 30).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(5);
        scene.addInstruction(new SetVatSideTypePonderInstruction(barometer, VatSideBlockEntity.DisplayType.BAROMETER));
        scene.idle(55);
        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .independent();
        scene.idle(20);
        BlockPos controller = util.grid().at(4, 2, 3);
        scene.addInstruction(new SetVatPressurePonderInstruction(controller, 200000f, 0.1f));
        scene.idle(20);
        scene.addInstruction(new SetVatPressurePonderInstruction(controller, -200000f, 0.05f));
        scene.idle(40);
        scene.addInstruction(new SetVatPressurePonderInstruction(controller, 0f, 0.1f));
        scene.idle(20);

        scene.world().showSection(util.select().position(2, 0, 9), Direction.NORTH);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(2, 1, 8, 2, 2, 8), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(1, 2, 7), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(1, 1, 6, 1, 2, 6), Direction.EAST);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(2, 1, 5, 2, 2, 6), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 1, 4), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 2, 2, 4).substract(util.select().position(2, 1, 4)), Direction.EAST);
        scene.idle(5);

        scene.overlay().showText(360)
            .text("This text is defined in a language file.")
            .independent(0)
            .attachKeyFrame();
        scene.idle(20);
        scene.addInstruction(new SetVatPressurePonderInstruction(controller, 250000f, 0.05f));
        scene.idle(80);

        scene.world().moveSection(vat, util.vector().of(0d, 1d, 0d), 20);
        scene.world().moveSection(roof, util.vector().of(0d, 1d, 0d), 20);
        scene.world().hideSection(util.select().fromTo(1, 1, 2, 2, 2, 4), Direction.WEST);
        scene.idle(20);
        scene.overlay().showText(240)
            .text("This text is defined in a language file.")
            .independent(40)
            .attachKeyFrame();
        scene.idle(20);
        ElementLink<WorldSectionElement> burners = scene.world().showIndependentSection(util.select().fromTo(4, 1, 1, 5, 1, 2), Direction.SOUTH);
        scene.world().moveSection(burners, util.vector().of(0d, 0d, 3d), 20);
        scene.idle(20);
        scene.addInstruction(new SetVatPressurePonderInstruction(controller, 500000f, 0.05f));
        scene.idle(60);

        scene.world().hideIndependentSection(burners, Direction.NORTH);
        scene.idle(20);
        scene.world().moveSection(vat, util.vector().of(0d, -1d, 0d), 20);
        scene.world().moveSection(roof, util.vector().of(0d, -1d, 0d), 20);
        scene.idle(20);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .independent(80)
            .attachKeyFrame();
        scene.idle(20);
        ElementLink<WorldSectionElement> piping = scene.world().showIndependentSection(util.select().fromTo(1, 1, 5, 2, 3, 5).substract(util.select().position(2, 1, 5)), Direction.EAST);
        scene.idle(20);
        scene.addInstruction(new SetVatPressurePonderInstruction(controller, 750000f, 0.05f));
        scene.idle(80);

        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .colored(PonderPalette.GREEN)
            .independent();
        scene.idle(100);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(util.grid().at(3, 4, 5), Direction.WEST))
            .attachKeyFrame();
        scene.idle(20);
        scene.world().hideIndependentSection(roof, Direction.UP);
        scene.idle(20);
        ElementLink<WorldSectionElement> glassCeiling = scene.world().showIndependentSection(util.select().fromTo(4, 5, 4, 5, 5, 5), Direction.DOWN);
        scene.world().moveSection(glassCeiling, util.vector().of(0d, -1d, 0d), 0);
        scene.idle(20);
        scene.world().hideIndependentSection(glassCeiling, Direction.UP); // Break the glass ceiling
        scene.idle(20);
        ElementLink<WorldSectionElement> copperRoof = scene.world().showIndependentSection(util.select().fromTo(4, 6, 4, 5, 6, 5), Direction.DOWN);
        scene.world().moveSection(copperRoof, util.vector().of(0d, -2d, 0d), 0);
        scene.idle(40);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .independent()
            .attachKeyFrame();
        scene.idle(20);
        scene.addInstruction(new SetVatPressurePonderInstruction(controller, 1000000f, 0.05f));
        scene.idle(40);
        scene.addInstruction(new FadeOutOfSceneInstruction<>(0, Direction.DOWN, vat));
        scene.addInstruction(new FadeOutOfSceneInstruction<>(0, Direction.DOWN, piping));
        scene.addInstruction(new FadeOutOfSceneInstruction<>(0, Direction.DOWN, copperRoof));
        scene.addInstruction(new ExplodePonderInstruction(level -> {
            // 1.21 Explosion ctor takes 13 args. Damage source built via
            // DamageTypes.EXPLOSION Holder lookup; vanilla default damage calculator (null).
            Holder<DamageType> dtHolder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.EXPLOSION);
            DamageSource damageSource = new DamageSource(dtHolder, null, null);
            Holder<SoundEvent> sound = Holder.direct(SoundEvents.GENERIC_EXPLODE.value());
            return new Explosion(level, null, damageSource, null,
                5d, 3d, 5d, 80f, false, Explosion.BlockInteraction.DESTROY,
                ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, sound);
        }));
        scene.idle(60);

        scene.markAsFinished();
    }

    /** Narrative:
 * <ol>
 * <li>Show initial thermometer gauge at topOf(2, 0, 0) with 450-tick lifetime + chase to
 * 0.5f (warming up to room temp).</li>
 * <li>Reveal bubble-cap tower (Reboiler + 2 caps), prompt user.</li>
 * <li>Drain reboiler 2000mB + emit DISTILLATION gas particles; fill top cap internal tank
 * + setTicksToFill timing (per-cap equilibrium delay from {@link BubbleCapBlockEntity}
 * API — getTankCapacity / getTransferRate).</li>
 * <li>Reveal second scene region (vat-like); spawn second thermometer at topOf(3, 3, 3)
 * with 0.75f init + chase to 0.5f (cooling down to room temp from above).</li>
 * </ol>
 *
 * <ul>
 * <li>{@link ThermometerInstruction} used in a non-Vat-controller context
 * (uses pure pointingAt Vec3, no BE binding). Two gauges coexist in one scene via
 * independent {@link ThermometerElement} handles via the
 * {@code Consumer<PonderScene>} factory pattern.</li>
 * <li>{@link BubbleCapBlockEntity} API: {@code getTank / getInternalTank /
 * getTankCapacity / getTransferRate / setTicksToFill}.</li>
 * <li>{@link GasParticleData} + {@link DestroyParticleTypes#DISTILLATION}.</li>
 * <li>{@link ProcessingPonderScenes#PURPLE_FLUID} display-potion substitute (PotionFluid
 * API pattern).</li>
 * <li>{@link VecHelper#getCenterOf(BlockPos)} catnip.</li>
 * </ul>
*/
    public static void roomTemperature(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("pollution.room_temperature", "This text is defined in a language file.");
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.75f);

        GasParticleData particleData = new GasParticleData(DestroyParticleTypes.DISTILLATION.get(), ProcessingPonderScenes.PURPLE_FLUID, 1.7f);
        BlockPos reboiler = util.grid().at(0, 1, 1);
        scene.world().modifyBlockEntity(reboiler, BubbleCapBlockEntity.class, bc -> bc.getTank().fill(ProcessingPonderScenes.PURPLE_FLUID, FluidAction.EXECUTE));

        scene.showBasePlate();

        scene.idle(10);
        ThermometerElement thermo = ThermometerInstruction.add(scene, util.vector().topOf(2, 0, 0), 450, 0.25f);
        scene.idle(10);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .independent();
        scene.addInstruction(ThermometerInstruction.chase(thermo, 0.5f, 0.0025f));
        scene.idle(120);

        scene.world().showSection(util.select().fromTo(0, 1, 1, 0, 3, 1), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(120)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(util.grid().at(0, 2, 1), Direction.WEST))
            .attachKeyFrame();
        scene.idle(40);
        scene.world().modifyBlockEntity(reboiler, BubbleCapBlockEntity.class, be -> be.getTank().drain(2000, FluidAction.EXECUTE));
        scene.effects().emitParticles(VecHelper.getCenterOf(reboiler), scene.effects().simpleParticleEmitter(particleData, Vec3.ZERO), 1.0f, 2);
        scene.world().modifyBlockEntity(util.grid().at(0, 3, 1), BubbleCapBlockEntity.class, be -> {
            be.getInternalTank().fill(ProcessingPonderScenes.PURPLE_FLUID, FluidAction.EXECUTE);
            be.setTicksToFill(BubbleCapBlockEntity.getTankCapacity() / BubbleCapBlockEntity.getTransferRate());
        });
        scene.idle(120);

        scene.world().showSection(util.select().fromTo(2, 1, 2, 4, 3, 4), Direction.DOWN);
        scene.idle(10);
        ThermometerElement vatThermo = ThermometerInstruction.add(scene, util.vector().topOf(3, 3, 3), 110, 0.75f);
        scene.idle(30);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(util.grid().at(2, 2, 3), Direction.WEST));
        scene.addInstruction(ThermometerInstruction.chase(vatThermo, 0.5f, 0.0025f));
        scene.idle(120);

        scene.markAsFinished();
    }

    /** Complex 6-phase narrative demonstrating
 * Vat gauge → redstone → NixieTube → DisplayLink instrumentation chain:
 * <ol>
 * <li>Reveal Vat frame; wrench-cycle thermometer side NORMAL → THERMOMETER; wrench-cycle
 * barometer side NORMAL → THERMOMETER → BAROMETER (demonstrates cycling).</li>
 * <li>Reveal 2 redstone-observer blocks (north of each gauge); toggle POWERED via
 * {@code cycleBlockProperty(pos, BlockStateProperties.POWERED)} + indicateRedstone;
 * flip both gauges to _BLOCKED variants.</li>
 * <li>Reveal 2 NixieTubes; directly write {@code RedstoneStrength} NBT (6 / 1) for display.</li>
 * <li>Right-click thermometer → {@code RedstoneStrength=11} (high reading). Text explaining
 * redstone-based threshold reads.</li>
 * <li>Reset via POWERED cycle + RedstoneStrength=0 → 15 → 10 cycle demo.</li>
 * <li>Reveal ThresholdSwitchBlock (2,2,4) + 2 redstone blocks; cycle `LEVEL` property +
 * write POWER=4 → POWER=8 through the redstone pair.</li>
 * <li>Reveal lectern + DisplayLink; show DISPLAY_LINK item right-click + outline the
 * lectern target + flashDisplayLink animation.</li>
 * </ol>
*/
    public static void vatReading(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("vat.reading", "This text is defined in a language file.");
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.7f);
        scene.showBasePlate();

        BlockPos thermometer = util.grid().at(5, 2, 3);
        BlockPos barometer = util.grid().at(4, 2, 3);

        scene.idle(10);
        scene.world().showSection(util.select().fromTo(3, 1, 3, 6, 4, 6), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(thermometer, Direction.WEST));
        scene.idle(20);
        scene.overlay().showControls(util.vector().blockSurface(thermometer, Direction.NORTH), Pointing.RIGHT, 60).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(5);
        scene.addInstruction(new SetVatSideTypePonderInstruction(thermometer, VatSideBlockEntity.DisplayType.THERMOMETER));
        scene.idle(75);
        scene.overlay().showText(110)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(barometer, Direction.WEST))
            .attachKeyFrame();
        scene.idle(20);
        scene.overlay().showControls(util.vector().blockSurface(barometer, Direction.NORTH), Pointing.RIGHT, 40).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(5);
        scene.addInstruction(new SetVatSideTypePonderInstruction(barometer, VatSideBlockEntity.DisplayType.THERMOMETER));
        scene.idle(45);
        scene.overlay().showControls(util.vector().blockSurface(barometer, Direction.NORTH), Pointing.RIGHT, 40).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(5);
        scene.addInstruction(new SetVatSideTypePonderInstruction(barometer, VatSideBlockEntity.DisplayType.BAROMETER));
        scene.idle(65);

        scene.world().showSection(util.select().fromTo(4, 1, 2, 5, 2, 2), Direction.DOWN);
        scene.idle(10);
        scene.addInstruction(new SetVatSideTypePonderInstruction(thermometer, VatSideBlockEntity.DisplayType.THERMOMETER_BLOCKED));
        scene.addInstruction(new SetVatSideTypePonderInstruction(barometer, VatSideBlockEntity.DisplayType.BAROMETER_BLOCKED));
        scene.effects().indicateRedstone(barometer.north());
        scene.effects().indicateRedstone(thermometer.north());
        scene.world().cycleBlockProperty(barometer.north(), BlockStateProperties.POWERED);
        scene.world().cycleBlockProperty(thermometer.north(), BlockStateProperties.POWERED);
        scene.idle(20);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.idle(40);
        scene.world().showSection(util.select().fromTo(4, 1, 1, 5, 2, 1), Direction.DOWN);
        scene.idle(10);
        scene.world().modifyBlockEntityNBT(util.select().position(5, 2, 1), NixieTubeBlockEntity.class, nbt -> nbt.putInt("RedstoneStrength", 6));
        scene.world().modifyBlockEntityNBT(util.select().position(4, 2, 1), NixieTubeBlockEntity.class, nbt -> nbt.putInt("RedstoneStrength", 1));
        scene.idle(70);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(thermometer, Direction.WEST))
            .attachKeyFrame();
        scene.idle(20);
        scene.overlay().showControls(util.vector().blockSurface(thermometer, Direction.NORTH, 0.25f), Pointing.RIGHT, 80).rightClick();
        scene.idle(40);
        scene.world().modifyBlockEntityNBT(util.select().position(5, 2, 1), NixieTubeBlockEntity.class, nbt -> nbt.putInt("RedstoneStrength", 11));
        scene.idle(60);

        scene.overlay().showText(200)
            .text("This text is defined in a language file.")
            .independent();
        scene.idle(20);
        scene.world().cycleBlockProperty(thermometer.north(), BlockStateProperties.POWERED);
        scene.effects().indicateRedstone(thermometer.north());
        scene.world().modifyBlockEntityNBT(util.select().position(5, 2, 1), NixieTubeBlockEntity.class, nbt -> nbt.putInt("RedstoneStrength", 0));
        scene.idle(40);
        scene.overlay().showText(140)
            .text("This text is defined in a language file.")
            .independent(40);
        scene.idle(20);
        scene.world().cycleBlockProperty(thermometer.north(), BlockStateProperties.POWERED);
        scene.effects().indicateRedstone(thermometer.north());
        scene.world().modifyBlockEntityNBT(util.select().position(5, 2, 1), NixieTubeBlockEntity.class, nbt -> nbt.putInt("RedstoneStrength", 15));
        scene.idle(40);
        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .colored(PonderPalette.GREEN)
            .independent(80);
        scene.idle(20);
        scene.world().modifyBlockEntityNBT(util.select().position(5, 2, 1), NixieTubeBlockEntity.class, nbt -> nbt.putInt("RedstoneStrength", 10));
        scene.effects().indicateRedstone(thermometer.north());
        scene.idle(80);

        BlockPos tswitch = util.grid().at(2, 2, 4);
        BlockPos rs1 = util.grid().at(1, 2, 3);
        BlockPos rs2 = util.grid().at(1, 2, 4);
        Selection redstone = util.select().fromTo(rs1, rs2);
        scene.world().showSection(util.select().fromTo(1, 1, 3, 2, 2, 4), Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(tswitch, Direction.WEST))
            .attachKeyFrame();
        scene.idle(20);
        scene.world().cycleBlockProperty(tswitch, ThresholdSwitchBlock.LEVEL);
        scene.world().modifyBlocks(redstone, s -> s.setValue(BlockStateProperties.POWER, 4), false);
        scene.effects().indicateRedstone(rs1);
        scene.effects().indicateRedstone(rs2);
        scene.idle(60);
        scene.world().cycleBlockProperty(tswitch, ThresholdSwitchBlock.LEVEL);
        scene.world().modifyBlocks(redstone, s -> s.setValue(BlockStateProperties.POWER, 8), false);
        scene.effects().indicateRedstone(rs1);
        scene.effects().indicateRedstone(rs2);
        scene.idle(40);

        BlockPos lectern = util.grid().at(1, 1, 6);
        BlockPos link = util.grid().at(4, 5, 4);
        scene.world().showSection(util.select().position(lectern), Direction.DOWN);
        scene.idle(20);
        scene.addKeyframe();
        scene.overlay().showControls(util.vector().blockSurface(lectern, Direction.UP), Pointing.DOWN, 40).withItem(AllBlocks.DISPLAY_LINK.asStack()).rightClick();
        scene.idle(5);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, "lectern", new AABB(lectern), 35);
        scene.idle(40);
        scene.world().showSection(util.select().position(link), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(link, Direction.WEST));
        scene.idle(20);
        scene.world().flashDisplayLink(link);
        scene.idle(100);

        scene.markAsFinished();
    }

    /** Narrative:
 * <ol>
 * <li>Reveal Vat frame (3×3 cube) + Colorimeter adjacent; show rotation animation (-90°
 * rotated + offset → snap back into position via {@code rotateSection / moveSection}).</li>
 * <li>Cycle {@link ColorimeterBlock#POWERED} property (blushing visual state flip).</li>
 * <li>Right-click prompt on Colorimeter top face (GUI hint — the BE has no actual GUI).</li>
 * <li>Reveal 2nd Colorimeter separate from vat; demonstrate side-attach constraint with
 * RED/GREEN chaseBoundingBoxOutline (invalid c2 RED, valid c1 GREEN).</li>
 * <li>Reveal redstone + NixieTube; drive POWER=4/10 and display RedstoneStrength on
 * NixieTube (molecule concentration readout demo).</li>
 * </ol>
 *
 * <ul>
 * <li>{@link ColorimeterBlock#POWERED} BooleanProperty exposes the powered state.</li>
 * <li>{@link NixieTubeBlockEntity} RedstoneStrength NBT.</li>
 * <li>Pure Ponder API: {@code rotateSection / moveSection / cycleBlockProperty /
 * chaseBoundingBoxOutline / modifyBlock / indicateRedstone / modifyBlockEntityNBT /
 * showIndependentSection / hideIndependentSection}.</li>
 * <li>{@link BlockStateProperties#POWER} vanilla.</li>
 * <li>{@link AABB} single-arg BlockPos ctor.</li>
 * </ul>
 *
 * <p>The ColorimeterBlock BE does not drive a real GUI / redstone-monitor readout, but the
 * scene's visual narrative works via the blockstate + NixieTube display workflows.</p>
*/
    public static void colorimeter(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("colorimeter", "This text is defined in a language file.");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(2, 1, 1, 4, 3, 4), Direction.DOWN);
        scene.idle(10);

        BlockPos colorimeter = util.grid().at(1, 2, 2);

        scene.world().showSection(util.select().position(0, 1, 2), Direction.DOWN);
        scene.idle(10);
        ElementLink<WorldSectionElement> c1 = scene.world().showIndependentSection(util.select().position(colorimeter), Direction.DOWN);
        scene.world().rotateSection(c1, 0, 90d, 0, 0);
        scene.world().moveSection(c1, util.vector().of(-1d, 0d, 0d), 0);
        scene.idle(20);
        scene.world().rotateSection(c1, 0d, -90d, 0d, 15);
        scene.world().moveSection(c1, util.vector().of(1d, 0d, 0d), 15);
        scene.idle(15);
        scene.world().cycleBlockProperty(colorimeter, ColorimeterBlock.POWERED);
        scene.idle(5);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(colorimeter, Direction.WEST));
        scene.idle(40);
        scene.overlay().showControls(util.vector().topOf(colorimeter), Pointing.DOWN, 60).rightClick();
        scene.idle(80);

        ElementLink<WorldSectionElement> c2 = scene.world().showIndependentSection(util.select().position(3, 2, 0), Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().topOf(3, 2, 0))
            .attachKeyFrame();
        scene.idle(20);
        scene.world().moveSection(c1, util.vector().of(-1d, 0d, 0d), 15);
        scene.world().moveSection(c2, util.vector().of(0d, 0d, -1d), 15);
        scene.idle(30);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, "c2", new AABB(util.grid().at(3, 2, 1)), 50);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "c1", new AABB(colorimeter.east()), 50);
        scene.idle(70);

        scene.world().moveSection(c1, util.vector().of(1d, 0d, 0d), 15);
        scene.world().hideIndependentSection(c2, Direction.UP);
        scene.idle(20);
        scene.world().showSection(util.select().fromTo(0, 1, 1, 0, 2, 2).substract(util.select().position(0, 1, 2)), Direction.DOWN);
        scene.idle(10);
        BlockPos redstone = util.grid().at(0, 2, 2);
        scene.world().modifyBlock(redstone, s -> s.setValue(BlockStateProperties.POWER, 4), false);
        scene.effects().indicateRedstone(redstone);
        scene.world().modifyBlockEntityNBT(util.select().position(0, 2, 1), NixieTubeBlockEntity.class, nbt -> nbt.putInt("RedstoneStrength", 4));
        scene.idle(10);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(colorimeter, Direction.WEST))
            .attachKeyFrame();
        scene.idle(60);
        scene.world().modifyBlock(redstone, s -> s.setValue(BlockStateProperties.POWER, 10), false);
        scene.effects().indicateRedstone(redstone);
        scene.world().modifyBlockEntityNBT(util.select().position(0, 2, 1), NixieTubeBlockEntity.class, nbt -> nbt.putInt("RedstoneStrength", 10));
        scene.idle(80);

        scene.markAsFinished();
    }

    /** Delegates to
 * {@link #vatUV(SceneBuilder, SceneBuildingUtil, boolean)} with {@code includeBlacklight=false}.
 * Used as Ponder scene on {@code POLLUTION_SYMBOL} tab (pollution-side entry, scene 9).
*/
    public static void vatUVWithoutBlackLight(SceneBuilder scene, SceneBuildingUtil util) {
        vatUV(scene, util, false);
    }

    /** Delegates to
 * {@link #vatUV(SceneBuilder, SceneBuildingUtil, boolean)} with {@code includeBlacklight=true}.
 * Used as Ponder scene on {@link petrolpark.mc.destroy.DestroyBlocks#BLACKLIGHT BLACKLIGHT}
 * block (wrench-right-click on placed block opens this scene) + {@code VAT_CONTROLLER} last tab.
*/
    public static void vatUVWithBlackLight(SceneBuilder scene, SceneBuildingUtil util) {
        vatUV(scene, util, true);
    }

    /**
 * Shared helper for {@link #vatUVWithoutBlackLight} + {@link #vatUVWithBlackLight}. Narrative:
 * <ol>
 * <li>Reveal small 3×3×3 vat frame. Show UV info text pointing at west face.</li>
 * <li>Show text pointing at top face explaining sunlight vs. UV light.</li>
 * <li>Independent overlay text explaining reaction kinetics 80-tick delay.</li>
 * <li>If {@code includeBlacklight} is true: reveal blacklight lamp attached to west face
 * + text prompt explaining artificial UV supplement.</li>
 * </ol>
*/
    public static void vatUV(SceneBuilder scene, SceneBuildingUtil util, boolean includeBlacklight) {
        scene.title("vat.uv", "This text is defined in a language file.");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 3, 3, 3), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(util.grid().at(1, 2, 2), Direction.WEST));
        scene.idle(100);
        scene.overlay().showText(140)
            .text("This text is defined in a language file.")
            .attachKeyFrame()
            .pointAt(util.vector().topOf(2, 3, 2));
        scene.idle(40);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .independent(80);
        scene.idle(120);

        if (!includeBlacklight) {
            scene.markAsFinished();
            return;
        }

        scene.world().showSection(util.select().position(0, 2, 2), Direction.EAST);
        scene.idle(10);
        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(util.grid().at(0, 2, 2), Direction.WEST));
        scene.idle(100);

        scene.markAsFinished();
    }

    /**
 * 2D periodic-table block positions used by {@link #periodicTable} to render a GRAY_CONCRETE
 * fallback grid — shape matches the empty "gaps" of a Mendeleev-layout periodic table for
 * elements not covered by {@link PeriodicTableBlock#ELEMENTS}. Paired int stride: positions[2i] = x, positions[2i+1] = y.
*/
    public static int[] defaultPositions = new int[]{
                16,  9, 15,  9, 14,  9, 13,  9, 12,  9, 11,  9, 10,  9,  9,  9,  8,  9,  7,  9,  6,  9,  5,  9,  4,  9,  3,  9,  2,  9,
                16,  8, 15,  8, 14,  8, 13,  8, 12,  8, 11,  8, 10,  8,  9,  8,  8,  8,  7,  8,  6,  8,  5,  8,  4,  8,  3,  8,  2,  8,

        17,  6, 16,  6, 15,  6, 14,  6, 13,  6, 12,  6, 11,  6, 10,  6,  9,  6,  8,  6,  7,  6,  6,  6,  5,  6,  4,  6,  3,  6,          1,  6,  0,  6,
        17,  5, 16,  5, 15,  5, 14,  5, 13,  5, 12,  5, 11,  5, 10,  5,  9,  5,  8,  5,  7,  5,  6,  5,  5,  5,  4,  5,  3,  5,          1,  5,  0,  5,
        17,  4, 16,  4, 15,  4, 14,  4, 13,  4, 12,  4, 11,  4, 10,  4,  9,  4,  8,  4,  7,  4,  6,  4,  5,  4,  4,  4,  3,  4,  2,  4,  1,  4,  0,  4,
        17,  3, 16,  3, 15,  3, 14,  3, 13,  3, 12,  3, 11,  3, 10,  3,  9,  3,  8,  3,  7,  3,  6,  3,  5,  3,  4,  3,  3,  3,  2,  3,  1,  3,  0,  3,
        17,  2, 16,  2, 15,  2, 14,  2, 13,  2, 12,  2,                                                                                  1,  2,  0,  2,
        17,  1, 16,  1, 15,  1, 14,  1, 13,  1, 12,  1,                                                                                  1,  1,  0,  1,
        17,  0,                                                                                                                                  0,  0
    };

    /** Narrative:
 * <ol>
 * <li>Place GRAY_CONCRETE placeholders at every default position (85 cells forming the
 * Mendeleev layout outline — incl. lanthanide / actinide rows).</li>
 * <li>Iterate {@link PeriodicTableBlock#ELEMENTS} and replace corresponding cells with the
 * first registered block of each element (data-pack driven).</li>
 * <li>{@code showSection} reveal 1-by-1 (1-tick stagger); multi-block elements cycle through
 * their variants on a timer.</li>
 * <li>3 attachKeyFrame text overlays.</li>
 * </ol>
*/
    public static void periodicTable(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("periodic_table", "This text is defined in a language file.");
        scene.configureBasePlate(0, 0, 20);
        scene.showBasePlate();

        scene.scaleSceneView(0.5f);
        scene.rotateCameraY(35);

        Set<PeriodicTableEntry> entriesToCycle = new HashSet<>();
        int maxCycles = 1;

        for (int i = 0; i < defaultPositions.length; i += 2) {
            scene.world().setBlock(util.grid().at(18, 10, 3).below(defaultPositions[i + 1]).west(defaultPositions[i]), Blocks.GRAY_CONCRETE.defaultBlockState(), false);
        }

        for (PeriodicTableEntry element : PeriodicTableBlock.ELEMENTS) {
            if (element.blocks().size() != 0) {
                scene.world().setBlock(util.grid().at(18, 10, 3).below(element.y()).west(element.x()), element.blocks().get(0).defaultBlockState(), false);
                if (element.blocks().size() > 1) {
                    entriesToCycle.add(element);
                    maxCycles = Math.max(maxCycles, element.blocks().size());
                }
            }
        }

        for (int i = 0; i < defaultPositions.length; i += 2) {
            scene.world().showSection(util.select().position(util.grid().at(18, 10, 3).below(defaultPositions[i + 1]).west(defaultPositions[i])), Direction.NORTH);
            scene.idle(1);
        }

        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
            .text("This text is defined in a language file.");

        if (maxCycles > 1) {
            for (int i = 0; i < maxCycles; i++) {
                for (PeriodicTableEntry entry : entriesToCycle) {
                    scene.world().setBlock(util.grid().at(18, 11, 2).below(entry.y()).west(entry.y()), entry.blocks().get(i % entry.blocks().size()).defaultBlockState(), false);
                }
                scene.idle(10);
            }
        }
        scene.idle(100);

        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.idle(100);

        scene.markAsFinished();
    }

    // The vatUV(SceneBuilder, SceneBuildingUtil, boolean) helper above backs the two delegate
    // methods (vatUVWithoutBlackLight / vatUVWithBlackLight).
}
