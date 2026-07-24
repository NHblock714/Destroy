package petrolpark.mc.destroy.core.chemistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.joml.Quaternionf;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import petrolpark.mc.library.util.MathsHelper;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.gui.ILightingSettings;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.ponder.render.VirtualRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite;

import petrolpark.mc.destroy.chemistry.legacy.LegacyAtom;
import petrolpark.mc.destroy.chemistry.legacy.LegacyBond.BondType;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMolecularStructure.Topology.SideChainInformation;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.serializer.Branch;
import petrolpark.mc.destroy.chemistry.serializer.Edge;
import petrolpark.mc.destroy.chemistry.serializer.Node;
import petrolpark.mc.destroy.client.PushingQuadBakingVertexConsumer;

/**
 * 3D ball-and-stick molecule renderer. Consumed by the molecule-display GUI + JEI ingredient
 * batch-render path to draw a Mixture's constituent Molecules. Constructor walks the Mixture's
 * Formula, positions every atom + bond in 3D using the {@link Geometry} VSEPR map, and bakes
 * everything into a single {@link BakedModel} for cheap subsequent re-renders.
*/
public class MoleculeRenderer {

    protected String moleculeID;

    protected AABB bb;

    protected static final double SCALE = 23d;
    protected static final double BOND_LENGTH = SCALE / 2;

    /** Atoms and Bonds to render, ordered back-to-front after construction.*/
    List<Pair<Vec3, IRenderableMoleculePart>> RENDERED_OBJECTS;

    /**
 * Pre-baked {@link BakedModel} containing all atoms + bonds as {@link BakedQuad BakedQuads}. Render paths
 * below now dispatch a single {@link net.minecraft.client.renderer.entity.ItemRenderer#renderModelLists}
 * call instead of iterating RENDERED_OBJECTS every frame.
*/
    protected BakedModel model;

    public MoleculeRenderer(LegacySpecies molecule) {
        moleculeID = molecule.getFullID();
        bb = new AABB(Vec3.ZERO, Vec3.ZERO);
        RENDERED_OBJECTS = new ArrayList<>();

        // Monatomic Molecules
        if (molecule.getAtoms().size() == 1) {
            RENDERED_OBJECTS.add(Pair.of(Vec3.ZERO, new AtomRenderInstance(molecule.getAtoms().iterator().next())));

        // Cyclic Molecules
        } else if (molecule.isCyclic()) {
            Map<LegacyAtom, Vec3> cyclicAtomsAndLocations = new HashMap<>();
            molecule.getCyclicAtomsForRendering().forEach(pair -> {
                cyclicAtomsAndLocations.put(pair.getSecond(), pair.getFirst());
                RENDERED_OBJECTS.add(Pair.of(
                    pair.getFirst().scale(BOND_LENGTH),
                    new AtomRenderInstance(pair.getSecond())));
            });
            molecule.getCyclicBondsForRendering().forEach(bond -> {
                Vec3 sourceAtomLocation = cyclicAtomsAndLocations.get(bond.getSourceAtom());
                Vec3 zig = cyclicAtomsAndLocations.get(bond.getDestinationAtom()).subtract(sourceAtomLocation).normalize();
                RENDERED_OBJECTS.add(Pair.of(
                    sourceAtomLocation.scale(BOND_LENGTH).add(zig.scale(BOND_LENGTH / 2)),
                    BondRenderInstance.fromZig(bond.getType(), zig)));
            });
            molecule.getSideChainsForRendering().forEach(pair -> {
                SideChainInformation sideChainInfo = pair.getFirst();
                Vec3 zig = sideChainInfo.bondDirection();
                Vec3 cyclicAtomLocation = cyclicAtomsAndLocations.get(sideChainInfo.atom()).scale(BOND_LENGTH);
                Vec3 startLocation = cyclicAtomLocation.add(zig.scale(BOND_LENGTH));
                Vec3 startDirection = sideChainInfo.branchDirection();
                Vec3 startPlane = sideChainInfo.bondDirection().cross(sideChainInfo.branchDirection());
                RENDERED_OBJECTS.add(Pair.of(
                    cyclicAtomLocation.add(zig.scale(BOND_LENGTH / 2)),
                    BondRenderInstance.fromZig(sideChainInfo.bondType(), zig)));

                generateBranch(pair.getSecond(), startLocation, startDirection, startPlane, zig, true);
            });

        // Standard branched Molecules
        } else {
            Vec3 startLocation = new Vec3(0d, 0d, 0d);
            Vec3 startDirection = new Vec3(1d, 0d, -1d).normalize();
            Vec3 startPlane = new Vec3(1d, 0d, 1d).normalize();
            generateBranch(
                molecule.getRenderBranch(),
                startLocation,
                startDirection,
                startPlane,
                MathsHelper.rotate(startDirection, startPlane, 180d + (getGeometry(molecule.getRenderBranch().getNodes().get(1), false).getAngle() * 0.5d)),
                false);
        }

        // Order the Atoms and Bonds so the furthest back get Rendered first
        RENDERED_OBJECTS.sort((pair1, pair2) -> Double.compare(pair1.getFirst().z, pair2.getFirst().z));

        // Rescale the Renderer to fit every Atom
        for (Pair<Vec3, IRenderableMoleculePart> pair : RENDERED_OBJECTS) {
            bb = bb.minmax(new AABB(pair.getFirst(), pair.getFirst()));
        }

        // PushingQuadBakingVertexConsumer push-wrapper over 1.21 pull-based QuadBakingVertexConsumer.
        // 1 draw call per molecule render instead of ~8-12 per-part calls.
        IModelBuilder<?> builder = IModelBuilder.of(false, true, true, ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY,
            UnitTextureAtlasSprite.INSTANCE, RenderTypeGroup.EMPTY);
        PushingQuadBakingVertexConsumer buffer = new PushingQuadBakingVertexConsumer(builder::addUnculledFace);
        buffer.setTintIndex(-1);
        for (Pair<Vec3, IRenderableMoleculePart> pair : RENDERED_OBJECTS) {
            pair.getSecond().renderInto(buffer, pair.getFirst());
        }
        buffer.finish(); // flush the final quad — wrapper contract
        model = builder.build();
    }

    public int getWidth() {
        return (int) bb.getXsize();
    }

    public int getHeight() {
        return (int) bb.getYsize();
    }

    /** Draw all Atoms and Bonds in this Molecule.*/
    public void render(int xPosition, int yPosition, GuiGraphics graphics) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        Vec3 center = bb.getCenter();
        poseStack.translate(center.x - bb.minX + xPosition, center.y - bb.minY + yPosition, -200);
        poseStack.mulPose(Axis.YP.rotationDegrees(AnimationTickHolder.getRenderTime()));
        poseStack.translate(-center.x, -center.y, -center.z);

        UIRenderHelper.flipForGuiRender(poseStack);
        Lighting.setupFor3DItems();
        // instead of per-part iteration.
        Minecraft.getInstance().getItemRenderer().renderModelLists(model, ItemStack.EMPTY, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            poseStack, graphics.bufferSource().getBuffer(Sheets.cutoutBlockSheet()));
        graphics.flush();

        poseStack.popPose();
    }

    /**
 * Draw all Atoms and Bonds in this Molecule into the provided buffer. Used for batch rendering
 * molecules in JEI.
*/
    public void renderItem(int xPosition, int yPosition, int width, int height,
                            PoseStack poseStack, MultiBufferSource.BufferSource buffer) {
        float scale = Math.min(0.5f, Math.min((width + 2) / (getWidth() + 1f), (height + 2) / (getHeight() + 1f)));

        poseStack.pushPose();
        Vec3 center = bb.getCenter();
        poseStack.translate(xPosition + width / 2, yPosition + height / 2, 50);
        poseStack.mulPose(Axis.XP.rotationDegrees(-10));
        poseStack.mulPose(Axis.YP.rotationDegrees(AnimationTickHolder.getRenderTime()));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-center.x, -center.y, -center.z);

        UIRenderHelper.flipForGuiRender(poseStack);
        Lighting.setupFor3DItems();
        Minecraft.getInstance().getItemRenderer().renderModelLists(model, ItemStack.EMPTY, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            poseStack, buffer.getBuffer(Sheets.cutoutBlockSheet()));

        poseStack.popPose();
    }

    /**
 * Recursively generate the position of all Atoms in a chain, and all side chains.
*/
    public void generateBranch(Branch branch, Vec3 startLocation, Vec3 direction, Vec3 plane, Vec3 zig,
                                boolean addOneConnectionToFirstNode) {
        Vec3 location = new Vec3(startLocation.x, startLocation.y, startLocation.z);
        Vec3 zag = new Vec3(zig.x, zig.y, zig.z);

        int i = 0;

        for (Node node : branch.getNodes()) {
            // Mark the Atom for rendering at this location
            RENDERED_OBJECTS.add(Pair.of(new Vec3(location.x, location.y, location.z), new AtomRenderInstance(node.getAtom())));

            i++;

            // Determine the Geometry of this node
            Geometry geometry = getGeometry(node, addOneConnectionToFirstNode);
            if (addOneConnectionToFirstNode) addOneConnectionToFirstNode = false;

            // Determine the orientation of this Atom
            ConfinedGeometry confinedGeometry = geometry.confine(zag, plane, direction);

            zag = confinedGeometry.getZag();

            // Render side chains
            int j = 1;
            for (Entry<Branch, BondType> sideBranchAndBondType : node.getOrderedSideBranches()) {

                Vec3 sideZag;
                if (j == geometry.connections.size()) {
                    sideZag = confinedGeometry.getInverseZig();
                } else {
                    sideZag = confinedGeometry.getZag(j);
                }

                Branch sideBranch = sideBranchAndBondType.getKey();
                Vec3 newPlane = confinedGeometry.getZig().cross(sideZag);
                RENDERED_OBJECTS.add(Pair.of(location.add(sideZag.scale(0.5d * BOND_LENGTH)),
                    BondRenderInstance.fromZig(sideBranchAndBondType.getValue(), sideZag)));
                generateBranch(sideBranch, location.add(sideZag.scale(BOND_LENGTH)),
                    MathsHelper.rotate(sideZag, newPlane, 90d), newPlane, sideZag, true);
                j++;
            }

            if (i >= branch.getNodes().size()) break;

            BondType bondType = BondType.SINGLE;
            for (Edge edge : node.getEdges()) {
                if (edge.getSourceNode() == branch.getNodes().get(i - 1)) bondType = edge.bondType;
            }

            RENDERED_OBJECTS.add(Pair.of(location.add(zag.scale(0.5d * BOND_LENGTH)),
                BondRenderInstance.fromZig(bondType, zag)));

            location = location.add(zag.scale(BOND_LENGTH));
        }
    }

    private Geometry getGeometry(Node node, boolean addOne) {
        int connections = node.getEdges().size() + node.getSideBranches().size() + (addOne ? 1 : 0);
        return node.getAtom().getElement().getGeometry(connections);
    }

    public static enum Geometry {

        LINEAR(new Vec3(1d, 0d, 0d)),
        V_SHAPE(new Vec3(0.333333d, -0.942809d, 0d).normalize()),
        TRIGONAL_PLANAR(new Vec3(0.5d, 0.86602540378d, 0d).normalize(), new Vec3(0.5d, -0.86602540378d, 0d).normalize()),
        TRIGONAL_PYRAMIDAL(new Vec3(0.333333d, -0.942809d, 0d).normalize(), new Vec3(0.333333d, 0.471405d, 0.816497d).normalize()),
        TETRAHEDRAL(new Vec3(0.333333d, -0.942809d, 0d).normalize(), new Vec3(0.333333d, 0.471405d, 0.816497d).normalize(), new Vec3(0.333333d, 0.471405d, -0.816497d).normalize()),
        OCTAHEDRAL(new Vec3(1d, 0d, 0d), new Vec3(0d, 1d, 0d), new Vec3(0d, -1d, 0d), new Vec3(0d, 0d, 1d), new Vec3(0d, 0d, -1d));

        private static final Vec3 standardDirection = new Vec3(1d, 0d, 0d);
        private static final Vec3 inverseStandardDirection = new Vec3(-1d, 0d, 0d);

        final ImmutableList<Vec3> connections;

        Geometry(Vec3... connections) {
            this.connections = ImmutableList.copyOf(connections);
        }

        public double getAngle() {
            double angle = MathsHelper.angleBetween(standardDirection, connections.get(0), new Vec3(0d, 0d, 1d));
            return angle < 90d ? 180d - angle : angle;
        }

        public ConfinedGeometry confine(Vec3 zig, Vec3 plane, Vec3 direction) {
            if (plane.dot(direction) > 0.000001d)
                throw new IllegalStateException("Chains of Molecules being rendered in a plane must continue in a direction in that plane.");

            Vec3 rotationVec = zig.cross(standardDirection);
            double angle = MathsHelper.angleBetween(standardDirection, zig, rotationVec);

            Vec3 zag = MathsHelper.rotate(connections.get(0), rotationVec, angle);

            boolean flip = distanceFromPointToLine(zig.add(MathsHelper.rotate(zag, zig, 180d)), Vec3.ZERO, direction)
                < distanceFromPointToLine(zig.add(zag), Vec3.ZERO, direction);

            return new ConfinedGeometry(this, rotationVec, angle, flip);
        }

        public List<Vec3> getConnections(boolean includeInput) {
            if (!includeInput) return connections;
            List<Vec3> connectionsAndInput = new ArrayList<>(connections.size() + 1);
            connectionsAndInput.addAll(connections);
            connectionsAndInput.add(inverseStandardDirection);
            return connectionsAndInput;
        }
    }

    private static class ConfinedGeometry {
        final Geometry geometry;
        final Vec3 rotationAxis;
        final double angle;
        final boolean flip;

        private ConfinedGeometry(Geometry geometry, Vec3 rotationAxis, double angle, boolean flip) {
            this.geometry = geometry;
            this.rotationAxis = rotationAxis;
            this.angle = angle;
            this.flip = flip;
        }

        private Vec3 getZig() {
            return MathsHelper.rotate(Geometry.standardDirection, rotationAxis, angle);
        }

        private Vec3 getInverseZig() {
            return MathsHelper.rotate(Geometry.inverseStandardDirection, rotationAxis, angle);
        }

        private Vec3 getZag() {
            return getZag(0);
        }

        private Vec3 getZag(int index) {
            Vec3 unflipped = MathsHelper.rotate(geometry.connections.get(index), rotationAxis, angle);
            if (!flip) return unflipped;
            return MathsHelper.rotate(unflipped, MathsHelper.rotate(Geometry.standardDirection, rotationAxis, angle), 180d);
        }
    }

    public static double distanceFromPointToLine(Vec3 point, Vec3 linePoint, Vec3 lineDirection) {
        return (point.subtract(linePoint)).cross(lineDirection).length() / lineDirection.length();
    }

    protected static interface IRenderableMoleculePart {
        void render(GuiGraphics graphics, Vec3 location);

        void renderInto(VertexConsumer builder, Vec3 location);
    }

    protected static record BondRenderInstance(BondType type, Quaternionf rotation) implements IRenderableMoleculePart {

        private static final Vec3 bond = new Vec3(1d, 0d, 0d);

        public static BondRenderInstance fromZig(BondType type, Vec3 zig) {
            Vec3 z = zig.normalize();
            Vec3 axis = bond.cross(z);
            Quaternionf q = new Quaternionf(axis.x(), axis.y(), axis.z(),
                (float) bond.dot(z) + (float) Math.sqrt(bond.lengthSqr() * z.lengthSqr()));
            if (!q.normalize().isFinite())
                q = new Quaternionf(0f, 0f, 0f, 1f);

            return new BondRenderInstance(type, q);
        }

        @Override
        public void render(GuiGraphics graphics, Vec3 location) {
            PoseStack poseStack = graphics.pose();
            poseStack.pushPose();
            poseStack.translate(location.x, location.y, location.z);
            TransformStack.of(poseStack).rotateCentered(rotation);
            GuiGameElement.of(type().getPartial())
                .lighting(ILightingSettings.DEFAULT_FLAT)
                .scale(SCALE)
                .render(graphics, 0, 0);
            poseStack.popPose();
        }

        @Override
        public void renderInto(VertexConsumer builder, Vec3 location) {
            PoseStack poseStack = new PoseStack();

            // Models are flipped on the Y axis when rendered in the GUI. Since the whole molecule is
            // baked into one model + flipped once, each part's Y coord flips here so it ends up
            // right-side-up post-flip.
            poseStack.translate(location.x, -location.y, location.z);
            TransformStack.of(poseStack)
                .rotate(new Quaternionf(-rotation.x, rotation.y, -rotation.z, rotation.w));
            poseStack.scale((float) SCALE, (float) SCALE, (float) SCALE);

            Minecraft.getInstance().getBlockRenderer().getModelRenderer()
                .renderModel(poseStack.last(), builder, Blocks.AIR.defaultBlockState(),
                    type().getPartial().get(), 1, 1, 1,
                    0, OverlayTexture.NO_OVERLAY, VirtualRenderHelper.VIRTUAL_DATA, RenderType.solid());
        }
    }

    protected static record AtomRenderInstance(LegacyAtom atom) implements IRenderableMoleculePart {

        @Override
        public void render(GuiGraphics graphics, Vec3 location) {
            PoseStack poseStack = graphics.pose();
            poseStack.pushPose();
            poseStack.translate(location.x, location.y, location.z);
            GuiGameElement.of(atom.getPartial())
                .scale(SCALE)
                .render(graphics, 0, 0);
            poseStack.popPose();
        }

        @Override
        public void renderInto(VertexConsumer builder, Vec3 location) {
            PoseStack poseStack = new PoseStack();

            poseStack.translate(location.x, -location.y, location.z);
            poseStack.scale((float) SCALE, (float) SCALE, (float) SCALE);

            Minecraft.getInstance().getBlockRenderer().getModelRenderer()
                .renderModel(poseStack.last(), builder, Blocks.AIR.defaultBlockState(),
                    atom.getPartial().get(), 1, 1, 1,
                    0, OverlayTexture.NO_OVERLAY, VirtualRenderHelper.VIRTUAL_DATA, RenderType.solid());
        }
    }
}
