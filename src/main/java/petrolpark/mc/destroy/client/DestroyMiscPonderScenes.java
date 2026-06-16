package petrolpark.mc.destroy.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyItems;
import petrolpark.mc.destroy.core.chemistry.vat.VatSideBlockEntity;
import petrolpark.mc.destroy.core.chemistry.vat.VatSideBlockEntity.DisplayType;

/**
 * Miscellaneous Ponder scenes for Destroy.
 *
 * <p><b>Registration site</b> ({@link DestroyPonderScenes#register}):</p>
 * <ul>
 *   <li>{@link #vatInteraction} — BLAZE_BURNER block, tag CHEMISTRY</li>
 *   <li>{@link #reactions} — MECHANICAL_MIXER block, tag CHEMISTRY</li>
 *   <li>{@link #redstoneProgrammer} — REDSTONE_PROGRAMMER block</li>
 *   <li>{@link #uv} — NOT registered (kept for completeness; lit by BlacklightBlock externally)</li>
 * </ul>
 */
public class DestroyMiscPonderScenes {

    /**
     * Vat interaction scene — guides the player through the wrench DisplayType cycle
     * (thermometer / barometer / pipe / vent), pipe extraction, item-funnel input, the
     * redstone-controlled top vent, and heating / cooling via Blaze Burners or Refrigerstrayters.
     * Schematic: {@code assets/destroy/ponder/vat/interaction.nbt}.
     */
    public static void vatInteraction(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("vat_interaction", "This text is defined in a language file.");
        scene.configureBasePlate(0, 0, 6);
        scene.scaleSceneView(0.8f);
        scene.showBasePlate();

        Selection vat = util.select().fromTo(1, 1, 1, 4, 4, 4);
        BlockPos dialBlock = util.grid().at(3, 3, 1);
        BlockPos pipeBlock = util.grid().at(1, 2, 3);
        BlockPos pipe = util.grid().at(0, 2, 3);
        BlockState pipeState = AllBlocks.FLUID_PIPE.getDefaultState()
            .setValue(FluidPipeBlock.DOWN, false)
            .setValue(FluidPipeBlock.UP, false)
            .setValue(FluidPipeBlock.NORTH, false)
            .setValue(FluidPipeBlock.SOUTH, false)
            .setValue(FluidPipeBlock.EAST, true)
            .setValue(FluidPipeBlock.WEST, true);
        BlockPos bottomFunnel = util.grid().at(0, 2, 2);
        BlockPos vent = util.grid().at(3, 4, 2);
        BlockPos lever = util.grid().at(3, 4, 0);
        Selection everything = util.select().fromTo(0, 1, 0, 4, 5, 4);

        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 4, 4, 4), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.idle(20);
        scene.overlay().showControls(util.vector().blockSurface(dialBlock, Direction.NORTH), Pointing.RIGHT, 20)
            .withItem(AllItems.WRENCH.asStack());
        scene.idle(5);
        scene.world().modifyBlockEntity(dialBlock, VatSideBlockEntity.class, vatSide -> vatSide.setDisplayType(DisplayType.THERMOMETER));
        scene.idle(75);

        scene.overlay().showText(60)
            .text("This text is defined in a language file.")
            .colored(PonderPalette.RED)
            .attachKeyFrame();
        Selection edges = vat.substract(util.select().fromTo(2, 2, 1, 3, 3, 4))
            .substract(util.select().fromTo(2, 1, 2, 3, 4, 3))
            .substract(util.select().fromTo(1, 2, 2, 4, 3, 3));
        scene.overlay().showOutline(PonderPalette.RED, "vat_outside", edges, 60);
        scene.idle(80);

        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.idle(20);
        scene.overlay().showControls(util.vector().blockSurface(dialBlock, Direction.NORTH), Pointing.RIGHT, 20)
            .withItem(AllItems.WRENCH.asStack());
        scene.idle(5);
        scene.world().modifyBlockEntity(dialBlock, VatSideBlockEntity.class, vatSide -> vatSide.setDisplayType(DisplayType.BAROMETER));
        scene.idle(75);

        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.idle(20);
        scene.world().setBlock(pipe, pipeState, false);
        scene.world().showSection(util.select().position(pipe), Direction.EAST);
        scene.idle(12);
        scene.world().modifyBlockEntity(pipeBlock, VatSideBlockEntity.class, vatSide -> vatSide.setDisplayType(DisplayType.PIPE));
        scene.idle(68);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .colored(PonderPalette.RED)
            .pointAt(util.vector().blockSurface(pipeBlock, Direction.WEST));
        scene.idle(120);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.idle(10);
        scene.world().showSection(util.select().position(2, 5, 3), Direction.DOWN);
        scene.idle(20);
        ElementLink<EntityElement> itemEntity = scene.world().createItemEntity(
            util.vector().centerOf(util.grid().at(2, 9, 3)),
            util.vector().of(0f, -0.4f, 0f),
            DestroyItems.PLATINUM_INGOT.asStack());
        scene.idle(8);
        scene.world().modifyEntity(itemEntity, Entity::discard);
        scene.idle(22);
        scene.world().showSection(util.select().position(bottomFunnel), Direction.EAST);
        scene.idle(20);
        scene.world().flapFunnel(bottomFunnel, true);
        scene.world().createItemEntity(
            util.vector().centerOf(bottomFunnel).add(0.15f, -0.45f, 0),
            Vec3.ZERO,
            DestroyItems.PLATINUM_INGOT.asStack());
        scene.idle(40);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.idle(20);
        scene.overlay().showControls(util.vector().blockSurface(vent, Direction.UP), Pointing.DOWN, 20)
            .withItem(AllItems.WRENCH.asStack());
        scene.idle(5);
        scene.world().modifyBlockEntity(vent, VatSideBlockEntity.class, vatSide -> vatSide.setDisplayType(DisplayType.OPEN_VENT));
        scene.idle(95);

        scene.overlay().showText(80)
            .text("This text is defined in a language file.");
        scene.idle(10);
        scene.world().showSection(util.select().position(lever), Direction.SOUTH);
        scene.idle(20);
        scene.world().toggleRedstonePower(util.select().position(lever));
        scene.effects().indicateRedstone(lever);
        scene.world().modifyBlockEntity(vent, VatSideBlockEntity.class, vatSide -> vatSide.setDisplayType(DisplayType.CLOSED_VENT));
        scene.idle(50);

        ElementLink<WorldSectionElement> everythingLink = scene.world().makeSectionIndependent(everything);
        scene.world().moveSection(everythingLink, util.vector().of(0, 3, 0), 10);
        scene.idle(20);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.idle(10);
        ElementLink<WorldSectionElement> blazeBurners = scene.world().showIndependentSection(util.select().fromTo(5, 1, 2, 6, 1, 3), Direction.WEST);
        scene.world().moveSection(blazeBurners, util.vector().of(-3, 0, 0), 0);
        scene.idle(20);
        scene.world().hideIndependentSection(blazeBurners, Direction.WEST);
        scene.idle(20);
        ElementLink<WorldSectionElement> coolers = scene.world().showIndependentSection(util.select().fromTo(7, 1, 2, 8, 1, 3), Direction.WEST);
        scene.world().moveSection(coolers, util.vector().of(-5, 0, 0), 0);
        scene.idle(20);
        scene.world().moveSection(everythingLink, util.vector().of(0, -2, 0), 10);
        scene.idle(40);

        scene.markAsFinished();
    }

    /**
 * Reactions intro scene.
 * Shows a basin-and-burner setup with three text overlays explaining reaction fundamentals.
 * Self-contained (no custom block dependencies beyond vanilla Ponder schematic for
 * mechanical_mixer). Wired on {@code AllBlocks.MECHANICAL_MIXER} with
 * {@code DestroyPonderTags.CHEMISTRY} tag.
*/
    public static void reactions(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("reactions", "This text is defined in a language file.");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.5f);
        scene.showBasePlate();

        scene.world().showSection(util.select().everywhere().substract(util.select().position(2, 1, 2)).substract(util.select().fromTo(0, 0, 0, 8, 0, 8)), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(util.grid().at(4, 4, 5), Direction.UP));
        scene.idle(120);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.idle(120);

        BlockPos underBasin = util.grid().at(1, 1, 2);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(underBasin, Direction.WEST));
        scene.idle(20);
        scene.world().hideSection(util.select().position(underBasin), Direction.WEST);
        scene.idle(20);
        ElementLink<WorldSectionElement> burner = scene.world().showIndependentSection(util.select().position(2, 1, 2), Direction.WEST);
        scene.world().moveSection(burner, util.vector().of(-1d, 0d, 0d), 0);
        scene.idle(80);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        scene.idle(120);

        scene.markAsFinished();
    }

    /**
 * UV / Blacklight scene. Shows a
 * vat top (glass panel) + side blacklight, two text overlays pointing at relevant
 * schematic positions. Kept for completeness even though not registered.
 *
 * <p>Deps: BlacklightBlock — but scene uses only generic Ponder
 * showSection/showOutline/showText; the block is referenced only by schematic-side,
 * not by Java code in the storyboard.</p>
*/
    public static void uv(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("uv", "This text is defined in a language file.");
        scene.configureBasePlate(0, 0, 6);
        scene.scaleSceneView(0.8f);
        scene.showBasePlate();

        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 4, 4, 4), Direction.DOWN);
        scene.overlay().showText(60)
            .text("This text is defined in a language file.");
        scene.idle(80);

        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.WHITE, "top_glass", util.select().fromTo(2, 4, 2, 3, 4, 3), 80);
        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().of(3, 5, 3))
            .attachKeyFrame();
        scene.idle(100);

        scene.world().showSection(util.select().fromTo(0, 2, 2, 0, 3, 3), Direction.EAST);
        scene.idle(10);
        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(util.grid().at(0, 3, 2), Direction.WEST))
            .attachKeyFrame();
        scene.idle(100);

        scene.markAsFinished();
    }

    /**
 * Redstone Programmer scene. Demonstrates redstone-link receiver setup (3 blocks wrench-toggled),
 * right-click placement of REDSTONE_PROGRAMMER on middle link, stand-alone sneak-placement,
 * and oscillating redstone power demo (5×3 toggles). Dependencies:
 *
 * <ul>
 * <li>{@link DestroyBlocks#REDSTONE_PROGRAMMER} (registered with PROGRAMMER_UUID
 * + PROGRAMMER_PROGRAM DataComponents).</li>
 * <li>{@code RedstoneLinkBlock.RECEIVER}
 * ({@code com.simibubi.create.content.redstone.link}).</li>
 * <li>{@link AllItems#WRENCH} (Create).</li>
 * </ul>
 *
 * <p>Wired on {@code DestroyBlocks.REDSTONE_PROGRAMMER} in
 * {@link DestroyPonderScenes#register}. Plays during right-click on REDSTONE_PROGRAMMER
 * ItemStack in JEI/hand.</p>
*/
    public static void redstoneProgrammer(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("redstone_programmer", "This text is defined in a language file.");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        scene.idle(10);
        for (int i = 0; i < 9; i++) {
            scene.world().showSection(util.select().position(1 + i % 3, 1 + i / 3, 3), Direction.DOWN);
            scene.idle(5);
        }

        for (int i = 0; i < 3; i++) {
            BlockPos pos = util.grid().at(1 + i, 3, 3);
            scene.overlay().showControls(util.vector().blockSurface(pos, Direction.SOUTH)
                .add(0, 0, -3 / 16f), net.createmod.catnip.math.Pointing.DOWN, 10)
                .rightClick()
                .withItem(AllItems.WRENCH.asStack());
            scene.world().modifyBlock(pos, s -> s.cycle(RedstoneLinkBlock.RECEIVER), true);
            scene.idle(10);
        }

        scene.idle(20);
        Vec3 linkVec = util.vector().blockSurface(util.grid().at(2, 3, 3), Direction.SOUTH).add(0, 0, -3 / 16f);
        scene.overlay().showControls(linkVec, net.createmod.catnip.math.Pointing.DOWN, 80).rightClick().withItem(DestroyBlocks.REDSTONE_PROGRAMMER.asStack());
        scene.overlay().showText(80)
            .text("This text is defined in a language file.")
            .pointAt(util.vector().blockSurface(util.grid().at(2, 2, 3), Direction.UP))
            .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
            .text("This text is defined in a language file.");
        scene.idle(100);

        Vec3 placementPos = util.vector().blockSurface(util.grid().at(2, 0, 1), Direction.UP);
        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .pointAt(placementPos)
            .attachKeyFrame();
        scene.idle(20);
        scene.overlay().showControls(placementPos, net.createmod.catnip.math.Pointing.DOWN, 40).rightClick().whileSneaking().withItem(DestroyBlocks.REDSTONE_PROGRAMMER.asStack());
        scene.idle(40);
        scene.world().showSection(util.select().position(2, 1, 1), Direction.DOWN);
        scene.idle(80);

        scene.overlay().showText(100)
            .text("This text is defined in a language file.")
            .attachKeyFrame();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 3; j++) {
                net.createmod.ponder.api.scene.Selection selection = util.select().fromTo(1 + j, 1, 3, 1 + j, 3, 3);
                scene.world().toggleRedstonePower(selection);
                scene.idle(10);
            }
        }
        scene.markAsFinished();
    }
}
