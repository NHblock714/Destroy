package petrolpark.mc.destroy.core.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.content.processing.glassblowing.BlowpipeItem;

/**
 * Central hub for Destroy's common-side (server + client game-bus) event subscribers. Mirrors
 * {@link DestroyClientModEvents} but for {@code game-bus} events that fire on both logical sides
 * (world load/unload, resource reload, etc.) rather than mod-bus / client-only events.
*/
@EventBusSubscriber(modid = Destroy.MOD_ID)
public class DestroyCommonEvents {

    /**
 * Register Destroy's {@link net.minecraft.server.packs.resources.PreparableReloadListener}s
 * on datapack reload. Currently:
 * <ul>
 * <li>{@code CircuitPatternHandler.RELOAD_LISTENER} — scans
 * {@code data/<ns>/destroy_compat/circuit_patterns/*.json}.</li>
 * <li>{@code PeriodicTableBlock.Listener} — scans
 * {@code data/<ns>/destroy_compat/periodic_table_blocks.json} to populate
 * {@code PeriodicTableBlock.ELEMENTS} for grid-completion advancement checks.</li>
 * <li>{@code ExplosiveProperties.Listener} — scans
 * {@code data/<ns>/destroy_compat/explosive_items/*.json} to populate
 * {@code ITEM_EXPLOSIVE_PROPERTIES} map driving mixed-explosive property tables
 * (T2b mixedexplosive subsystem).</li>
 * </ul>
*/
    @SubscribeEvent
    public static final void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(Destroy.CIRCUIT_PATTERN_HANDLER.RELOAD_LISTENER);
        event.addListener(new petrolpark.mc.destroy.content.product.periodictable.PeriodicTableBlock.Listener());
        event.addListener(new petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties.Listener());
        // Vat materials datapack reload (T2a break-in).
        event.addListener(new petrolpark.mc.destroy.core.chemistry.vat.material.VatMaterialResourceListener());
        // Datapack-defined chemistry elements (data/<ns>/destroy/elements/). MUST register
        // BEFORE the molecule listener — molecules' FROWNS strings reference elements by
        // symbol via LegacyElement.fromSymbol, so elements must exist at parse time.
        event.addListener(new petrolpark.mc.destroy.core.chemistry.data.ElementDataReloadListener());
        // Datapack-defined chemistry molecules (data/<ns>/destroy/molecules/). MUST register
        // BEFORE the reaction listener — reactions reference molecules by id and need them
        // present in LegacySpecies.MOLECULES at apply-time.
        event.addListener(new petrolpark.mc.destroy.core.chemistry.data.MoleculeDataReloadListener());
        // Datapack-defined chemistry reactions (data/<ns>/destroy/reactions/).
        event.addListener(new petrolpark.mc.destroy.core.chemistry.data.ReactionDataReloadListener());
    }

    /**
     * Resend the current datapack reactions to a player on join. The listener's
     * {@code sendToAllClients} broadcast only reaches players already connected; late-joiners
     * need an individual send so their JEI Reaction category shows the same reactions as
     * everyone else. The cached {@link petrolpark.mc.destroy.core.chemistry.data.ReactionDataReloadListener#LAST_LOADED}
     * map avoids re-parsing every datapack JSON.
     */
    @SubscribeEvent
    public static void resyncDatapackReactionsOnJoin(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        net.minecraft.server.level.ServerPlayer player = event.getPlayer();
        if (player == null) return;  // null player = post-reload broadcast, the listener already handled it.

        // Send elements first, then molecules, then reactions — strict dependency order so the
        // client's apply() chain resolves symbols and ids in the same order the server did.
        var elements = petrolpark.mc.destroy.core.chemistry.data.ElementDataReloadListener.LAST_LOADED;
        if (!elements.isEmpty()) {
            net.createmod.catnip.platform.CatnipServices.NETWORK.sendToClient(player,
                new petrolpark.mc.destroy.core.chemistry.data.SyncElementsS2CPacket(elements));
        }
        var molecules = petrolpark.mc.destroy.core.chemistry.data.MoleculeDataReloadListener.LAST_LOADED;
        if (!molecules.isEmpty()) {
            net.createmod.catnip.platform.CatnipServices.NETWORK.sendToClient(player,
                new petrolpark.mc.destroy.core.chemistry.data.SyncMoleculesS2CPacket(molecules));
        }
        var reactions = petrolpark.mc.destroy.core.chemistry.data.ReactionDataReloadListener.LAST_LOADED;
        if (!reactions.isEmpty()) {
            net.createmod.catnip.platform.CatnipServices.NETWORK.sendToClient(player,
                new petrolpark.mc.destroy.core.chemistry.data.SyncReactionsS2CPacket(reactions));
        }
    }

    /**
     * Sync the world's generated circuit-board patterns to each joining player. A client joining a
     * running server otherwise never receives the randomised 4×4 patterns, so its circuit-board
     * crafting recipes render blank (or it generates a mismatched local pattern) until a
     * {@code /reload} or the regenerate command re-broadcasts. {@code getAllPatterns()} forces any
     * not-yet-generated pattern before sending; reload broadcasts are handled by
     * {@link petrolpark.mc.destroy.content.processing.trypolithography.CircuitPatternHandler.Listener},
     * so this only covers the per-player join case.
     */
    @SubscribeEvent
    public static void syncCircuitPatternsOnJoin(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        net.minecraft.server.level.ServerPlayer player = event.getPlayer();
        if (player == null) return;
        net.createmod.catnip.platform.CatnipServices.NETWORK.sendToClient(player,
            new petrolpark.mc.destroy.content.processing.trypolithography.CircuitPatternsS2CPacket(
                Destroy.CIRCUIT_PATTERN_HANDLER.getAllPatterns()));
    }

    /**
     * Sync the world's datapack-defined vat-shell materials to each joining player. Like the
     * circuit patterns, {@code VatMaterialResourceListener} only broadcasts on reload, so a client
     * joining a running server can't recognise datapack vat casings (or read their
     * pressure/conductivity/transparency) until a {@code /reload}. Only the non-built-in (datapack)
     * materials are sent — the client already registers the built-ins, and the packet clears its
     * datapack set before applying.
     */
    @SubscribeEvent
    public static void syncVatMaterialsOnJoin(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        net.minecraft.server.level.ServerPlayer player = event.getPlayer();
        if (player == null) return;
        java.util.HashMap<petrolpark.mc.library.core.data.recipe.ingredient.BlockIngredient<?>,
            petrolpark.mc.destroy.core.chemistry.vat.material.VatMaterial> datapackMaterials = new java.util.HashMap<>();
        petrolpark.mc.destroy.core.chemistry.vat.material.VatMaterial.BLOCK_MATERIALS.forEach((ingredient, material) -> {
            if (!material.builtIn()) datapackMaterials.put(ingredient, material);
        });
        net.createmod.catnip.platform.CatnipServices.NETWORK.sendToClient(player,
            new petrolpark.mc.destroy.core.chemistry.vat.material.SyncVatMaterialsS2CPacket(datapackMaterials));
    }


    /**
 *
 * <p>Why ServerAboutToStartEvent (not RegisterEvent / ModLoading): structure pools are runtime
 * world data — they don't exist at mod-init time. They're loaded from datapack JSONs at world
 * load, after which the loaded {@code StructureTemplatePool.templates} list is mutated
 * in-place. {@code ServerAboutToStartEvent} fires AFTER datapack load but BEFORE the world
 * starts generating chunks, so the inn pieces are part of the pool by the time the first
 * village structure is placed.</p>
*/
    @SubscribeEvent
    public static final void onServerAboutToStart(net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) {
        net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool> templatePoolRegistry =
            event.getServer().registryAccess().registry(net.minecraft.core.registries.Registries.TEMPLATE_POOL).orElseThrow();
        net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList> processorListRegistry =
            event.getServer().registryAccess().registry(net.minecraft.core.registries.Registries.PROCESSOR_LIST).orElseThrow();

        petrolpark.mc.destroy.core.DestroyVillageAddition.addBuildingToPool(
            templatePoolRegistry, processorListRegistry,
            net.minecraft.resources.ResourceLocation.parse("minecraft:village/plains/houses"),
            "destroy:plains_inn", 5);
        petrolpark.mc.destroy.core.DestroyVillageAddition.addBuildingToPool(
            templatePoolRegistry, processorListRegistry,
            net.minecraft.resources.ResourceLocation.parse("minecraft:village/desert/houses"),
            "destroy:desert_inn", 5);
    }

    /**
 * World-load bookkeeping for Destroy's per-level handlers. Both
 * {@link petrolpark.mc.destroy.content.processing.trypolithography.CircuitPuncherHandler#onLoadWorld}
 * (registers puncher map for the level) and
 * {@link petrolpark.mc.destroy.content.processing.trypolithography.CircuitPatternHandler#onLevelLoaded}
 * (attaches SavedData via {@code SavedData.Factory}) are called here.
*/
    @SubscribeEvent
    public static final void onLoadWorld(LevelEvent.Load event) {
        Destroy.CIRCUIT_PUNCHER_HANDLER.onLoadWorld(event.getLevel());
        Destroy.CIRCUIT_PATTERN_HANDLER.onLevelLoaded(event.getLevel());
    }

    /**
 * World-unload cleanup — mirror of {@link #onLoadWorld}. Clears both handlers' per-level state
 * to avoid cross-level leakage on save-load cycles.
*/
    @SubscribeEvent
    public static final void onUnloadWorld(LevelEvent.Unload event) {
        Destroy.CIRCUIT_PUNCHER_HANDLER.onUnloadWorld(event.getLevel());
        Destroy.CIRCUIT_PATTERN_HANDLER.onLevelUnloaded(event.getLevel());
    }

    /** Adds the baby-villager sand-search behaviour (the related
 * {@link petrolpark.mc.destroy.core.pollution.PollutionEvents#onEntityJoinLevel}
 * handles lightning-regenerates-ozone). Without this, baby villagers never gain the AI to
 * search for sand → no sand castle building, even with bucket-and-spade in inventory.
*/
    @SubscribeEvent
    public static final void onEntityJoinLevel(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.npc.Villager villager && villager.isBaby()) {
            villager.goalSelector.addGoal(0,
                new petrolpark.mc.destroy.content.sandcastle.BuildSandCastleGoal(villager, true));
        }

        // Award SHOOT_HEFTY_BEETROOT when a player fires a Hefty-Beetroot-tagged potato through a
        // Potato Cannon (Create). Mirrors upstream 1.20.1 hook.
        if (event.getEntity() instanceof com.simibubi.create.content.equipment.potatoCannon.PotatoProjectileEntity projectile
            && projectile.getOwner() instanceof net.minecraft.server.level.ServerPlayer player
            && petrolpark.mc.destroy.DestroyTags.Items.HEFTY_BEETROOTS.matches(projectile.getItem().getItem())) {
            petrolpark.mc.destroy.DestroyAdvancementTrigger.SHOOT_HEFTY_BEETROOT.award(player.level(), player);
        }
    }

    /**
 * Player left-click handler — currently routes Blowpipe finish-blowing to
 * {@link BlowpipeItem#finishBlowing}. Without this, after the long-press blow animation
 * completes, players can't extract the produced glass item — and because TANK/PROGRESS
 * stay set, they also can't refill or re-pick a recipe (the use() method's recipe-switch
 * check is gated on {@code progress == 0}).
*/
    @SubscribeEvent
    public static final void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        Level world = event.getLevel();
        ItemStack stack = event.getItemStack();
        net.minecraft.core.BlockPos pos = event.getPos();
        net.minecraft.world.level.block.state.BlockState state = world.getBlockState(pos);

        // Measuring Cylinder special path: open the transfer screen instead of
        // fill-everything (defaultAttack semantics). This cylinder-specific dispatch sits in front
        // of the generic IMixtureStorageItem.defaultAttack below. Without it, left-clicking a tank
        // with a measuring cylinder filled all-or-nothing instead of opening the slider GUI for
        // metered withdrawal. Must run BEFORE the generic IMixtureStorageItem branch to win the
        // cancel-event race.
        if (stack.getItem() instanceof petrolpark.mc.destroy.core.chemistry.storage.measuringcylinder.MeasuringCylinderBlockItem) {
            net.minecraft.world.ItemInteractionResult cylResult =
                petrolpark.mc.destroy.core.chemistry.storage.measuringcylinder.MeasuringCylinderBlockItem
                    .tryOpenTransferScreen(world, pos, state, event.getFace(), player, event.getHand(), stack, true);
            if (cylResult == net.minecraft.world.ItemInteractionResult.SUCCESS
                || cylResult == net.minecraft.world.ItemInteractionResult.CONSUME) {
                event.setCanceled(true);
                return;
            }
            // FAIL or PASS_TO_DEFAULT_BLOCK_INTERACTION → fall through to generic path so empty
            // tanks / no-cap targets can still be handled (they'll just no-op).
        }

        // IMixtureStorageItem fill-from-block on LEFT-click (test tube, beaker, etc.).
        // This branch enables left-click fluid extraction; without it, items like the test tube
        // can't draw fluid out of any container.
        if (stack.getItem() instanceof petrolpark.mc.destroy.core.chemistry.storage.IMixtureStorageItem mixtureItem) {
            net.minecraft.world.InteractionResult result =
                petrolpark.mc.destroy.core.chemistry.storage.IMixtureStorageItem.defaultAttack(
                    mixtureItem, world, pos, state, event.getFace(), player, event.getHand(), stack);
            if (result != net.minecraft.world.InteractionResult.PASS) {
                event.setCanceled(true);
                return;
            }
        }

        // IPickUpPutDownBlock fast-pickup. Without this branch, left-clicking a placed
        // beaker / round-bottomed flask / measuring cylinder etc. goes through vanilla normal
        // break flow:
        // - Survival: loot table fires, drops empty (fresh-NBT) item — content + capacity lost.
        // - Creative: vanilla skips drops entirely → block disappears + nothing in hand.
        // This branch instead clones the block entity's fluid contents into the dropped item NBT,
        // then {@code destroyBlock(pos, false)} (false = don't drop natively) +
        // {@code placeItemBackInInventory}, so both creative and survival return the NBT-preserving
        // item.
        // Must run AFTER IMixtureStorageItem.defaultAttack so that "left-click empty beaker with
        // test tube" does fluid extraction; only when the player's hand
        // can't extract does the fast-pickup fire.
        if (state.getBlock() instanceof petrolpark.mc.destroy.core.block.IPickUpPutDownBlock) {
            // No FakePlayer guard here, so Create
            // mechanical arm / deployer can physically transport filled glassware
            // (left-click pickup → arm holds NBT-preserving stack → put down elsewhere).
            net.minecraft.world.item.ItemStack cloneItemStack = state.getCloneItemStack(
                new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.ZERO, event.getFace(), pos, false),
                world, pos, player);
            world.destroyBlock(pos, false);
            if (world.getBlockState(pos) != state && !world.isClientSide()) {
                player.getInventory().placeItemBackInInventory(cloneItemStack);
            }
            event.setCanceled(true);
            return;
        }

        // Blowpipe finish-blowing — left-click any block while holding a finished Blowpipe pops
        // the glass result into the player's inventory. 1.21 NeoForge LeftClickBlock no longer
        // exposes setCancellationResult (was an old Forge API); setCanceled(true) suffices to
        // suppress the default break action.
        if (stack.getItem() instanceof BlowpipeItem blowpipe) {
            if (blowpipe.finishBlowing(stack, world, player)) {
                event.setCanceled(true);
                return;
            }
            // Left-click fluid extraction: matches Destroy's UX convention where every other
            // fluid-storage item (test tube / beaker / measuring cylinder, all via
            // IMixtureStorageItem.defaultAttack above) extracts on LEFT-click. Upstream's
            // BlowpipeItem only drained via useOn (right-click), an inconsistency since the
            // blowpipe couldn't extract molten borosilicate glass on left-click. Only fire when:
            //   1. The blowpipe has a recipe (REQUIRED_FLUID ingredient is set), and
            //   2. The TANK is currently empty (mid-blow or post-blow must not refill).
            net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient ingredient =
                petrolpark.mc.destroy.content.processing.glassblowing.BlowpipeItem.getFluidIngredient(stack);
            net.neoforged.neoforge.fluids.FluidStack tank = stack.getOrDefault(
                petrolpark.mc.destroy.DestroyDataComponents.BLOWPIPE_TANK,
                net.neoforged.neoforge.fluids.FluidStack.EMPTY);
            if (ingredient != null && tank.isEmpty()) {
                net.minecraft.world.InteractionResult result = blowpipe.tryDrainFromBlock(
                    world, pos, event.getFace(), stack, ingredient);
                if (result == net.minecraft.world.InteractionResult.SUCCESS
                    || result == net.minecraft.world.InteractionResult.FAIL) {
                    event.setCanceled(true);
                }
            }
        }
    }

    /** Without this handler the programmer item simply tries to place the block
 * (vanilla BlockItem fallback) → no frequency added → right-clicking a wireless redstone
 * terminal can't add its channel to the programmer.
*/
    @SubscribeEvent
    public static final void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level world = event.getLevel();
        ItemStack stack = event.getItemStack();
        net.minecraft.core.BlockPos pos = event.getPos();

        // Redstone Programmer + Redstone Link → add frequency to program
        if (stack.getItem() instanceof petrolpark.mc.destroy.content.redstone.programmer.RedstoneProgrammerBlockItem) {
            com.simibubi.create.content.redstone.link.LinkBehaviour link =
                com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(world, pos,
                    com.simibubi.create.content.redstone.link.LinkBehaviour.TYPE);
            if (link != null && !player.isShiftKeyDown()) {
                petrolpark.mc.destroy.content.redstone.programmer.RedstoneProgrammerBlockItem
                    .getProgram(stack, world, player).ifPresent(program -> {
                    net.createmod.catnip.data.Couple<com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency> key = link.getNetworkKey();
                    if (program.getChannels().stream().anyMatch(channel -> channel.getNetworkKey().equals(key))) {
                        if (world.isClientSide()) player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("destroy.tooltip.redstone_programmer.add_frequency.failure.exists")
                                .withStyle(net.minecraft.ChatFormatting.RED), true);
                    } else if (program.getChannels().size() >= petrolpark.mc.destroy.config.DestroyConfigs.server().blocks.redstoneProgrammerMaxChannels.get()) {
                        if (world.isClientSide()) player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("destroy.tooltip.redstone_programmer.add_frequency.failure.full")
                                .withStyle(net.minecraft.ChatFormatting.RED), true);
                    } else {
                        program.addBlankChannel(key);
                        petrolpark.mc.destroy.content.redstone.programmer.RedstoneProgrammerBlockItem
                            .setProgram(stack, program, world.registryAccess());
                        if (world.isClientSide()) player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("destroy.tooltip.redstone_programmer.add_frequency.success",
                                key.getFirst().getStack().getHoverName(), key.getSecond().getStack().getHoverName()), true);
                    }
                });
                event.setCanceled(true);
            }
        }

        // Fireproof Flint and Steel — used to light a fire from a fireproofed flint & steel
        // awards FIREPROOF_FLINT_AND_STEEL and consumes one durability.
        if (stack.getItem() == net.minecraft.world.item.Items.FLINT_AND_STEEL
            && petrolpark.mc.destroy.content.product.fireretardant.FireproofingHelper.isFireproof(world.registryAccess(), stack)) {
            petrolpark.mc.destroy.DestroyAdvancementTrigger.FIREPROOF_FLINT_AND_STEEL.award(world, player);
            stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    /** Particle + sound feedback during the 6-second pee cycle.
*/
    
    @SubscribeEvent
    public static final void onPlayerEntityInteractSpecific(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific event) {
        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(event.getHand());
        Level level = event.getLevel();

        // Branch 1: Stray capture
        if (com.simibubi.create.AllItems.EMPTY_BLAZE_BURNER.isIn(stack)
            && event.getTarget() instanceof net.minecraft.world.entity.monster.Stray stray) {
            // Skip if the burner already has something captured (Blaze / Wither Skeleton / etc.)
            if (stack.getItem() instanceof com.simibubi.create.content.processing.burner.BlazeBurnerBlockItem item) {
                if (item.hasCapturedBlaze()) return;
            }

            level.playSound(null, net.minecraft.core.BlockPos.containing(stray.position()),
                net.minecraft.sounds.SoundEvents.STRAY_HURT,
                net.minecraft.sounds.SoundSource.HOSTILE, 0.25f, 0.75f);
            stray.discard();

            ItemStack filled = petrolpark.mc.destroy.DestroyBlocks.COOLER.asStack();
            if (!player.isCreative()) stack.shrink(1);
            if (stack.isEmpty()) {
                player.setItemInHand(event.getHand(), filled);
            } else {
                player.getInventory().placeItemBackInInventory(filled);
            }

            petrolpark.mc.destroy.DestroyAdvancementTrigger.CAPTURE_STRAY.award(level, player);
            event.setCanceled(true);
            return;
        }

        // Branch 2: Tear collection (right-click crying entity with glass bottle)
        if (stack.is(net.minecraft.world.item.Items.GLASS_BOTTLE)
            && event.getTarget() instanceof net.minecraft.world.entity.LivingEntity le
            && le.hasEffect(petrolpark.mc.destroy.DestroyMobEffects.CRYING.getDelegate())) {
            le.removeEffect(petrolpark.mc.destroy.DestroyMobEffects.CRYING.getDelegate());
            ItemStack filled = petrolpark.mc.destroy.DestroyItems.TEAR_BOTTLE.asStack();
            if (!player.isCreative()) stack.shrink(1);
            if (stack.isEmpty()) {
                player.setItemInHand(event.getHand(), filled);
            } else {
                player.getInventory().placeItemBackInInventory(filled);
            }
            petrolpark.mc.destroy.DestroyAdvancementTrigger.COLLECT_TEARS.award(level, player);
            event.setCanceled(true);
        }
    }

    private static final java.util.WeakHashMap<java.util.UUID, int[]> URINATE_TICKS =
        new java.util.WeakHashMap<>();

    @SubscribeEvent
    public static final void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return; // server-only state machine
        net.minecraft.core.BlockPos posOn = player.getOnPos();
        net.minecraft.world.level.block.state.BlockState stateOn = player.level().getBlockState(posOn);
        boolean onCauldron = stateOn.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)
            || stateOn.is(net.minecraft.world.level.block.Blocks.CAULDRON);
        boolean canUrinate = onCauldron && player.hasEffect(petrolpark.mc.destroy.DestroyMobEffects.FULL_BLADDER.getDelegate());

        int[] state = URINATE_TICKS.computeIfAbsent(player.getUUID(), k -> new int[]{0});
        if (player.isCrouching() && canUrinate) {
            state[0]++;
        } else {
            state[0] = 0;
        }
        int ticksUrinating = state[0];

        if (ticksUrinating > 0) {
            // FluidFX.getFluidParticle
            // returns a {@link net.minecraft.core.particles.ParticleOptions} that is safe
            // to construct server-side; only the actual rendering happens on client. This uses
            // {@code ServerLevel.sendParticles} which builds a ClientboundLevelParticlesPacket
            // rather than {@code level.addParticle(...)} that only ran for the local client (LAN /
            // multiplayer observers wouldn't see the urine stream from another player otherwise).
            // Particle spec: 1 particle/tick at player.position()+0.5y, falling straight down at
            // 0.07 blocks/tick. sendParticles
            // signature is (particle, x, y, z, count, xOffset, yOffset, zOffset, speed); to get the
            if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                net.minecraft.world.phys.Vec3 pos = player.position();
                // Use count=0 special-case semantics. ServerLevel.sendParticles
                // signature is (particle, x, y, z, count, xDist, yDist, zDist, speed). When
                // count > 0, the (xDist, yDist, zDist) triple is treated as RANDOM POSITION
                // OFFSET RANGE and `speed` becomes a multiplier on a randomly-directed velocity
                // — that's why count=1 produced a splash-shaped scatter instead of a particle
                // moving straight down.
                // When count == 0, vanilla switches semantics: (xDist, yDist, zDist) becomes the
                // VELOCITY VECTOR for a single emitted particle, and `speed` is a multiplier on
                // that vector.
                // x, y, z, 0, -0.07, 0)} call which fed (vx, vy, vz) directly. Result: one
                // urine droplet per server tick falling straight down at ~0.07 blocks/tick,
                // bypass {@code com.simibubi.create.content.fluids.FluidFX.getFluidParticle}
                // which references {@code Minecraft.getInstance().level} (ClientLevel) at FluidFX.java:101.
                // Loading FluidFX on dedicated server triggers RuntimeDistCleaner rejection of
                // ClientLevel during class verification of an unrelated method — kicks the player on
                // the urinate-into-cauldron path because onPlayerTick is called every tick. FluidFX.getFluidParticle's
                // body is literally `new FluidParticleData(AllParticleTypes.FLUID_PARTICLE.get(), fluid)`
                // (Create FluidFX.java:46-48); replicate inline to avoid loading the FluidFX class.
                // FluidParticleData itself is server-safe: only its @OnlyIn(CLIENT) getFactory() method
                // pulls in ParticleProvider, and that's stripped by RuntimeDistCleaner on server.
                serverLevel.sendParticles(
                    new com.simibubi.create.content.fluids.particle.FluidParticleData(
                        com.simibubi.create.AllParticleTypes.FLUID_PARTICLE.get(),
                        new net.neoforged.neoforge.fluids.FluidStack(
                            petrolpark.mc.destroy.DestroyFluids.URINE.get(), 1000)),
                    pos.x, pos.y + 0.5, pos.z,
                    0,        // count=0 → switch to velocity-vector mode
                    0.0,      // vx
                    -0.07,    // vy
                    0.0,      // vz
                    1.0);     // speed multiplier (1.0 = use vector as-is)
            }
            if (ticksUrinating % 40 == 0) {
                player.level().playSound(null, posOn,
                    petrolpark.mc.destroy.DestroySoundEvents.URINATE.getMainEvent(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            if (ticksUrinating == 119 && player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                petrolpark.mc.destroy.DestroyMobEffects.increaseEffectLevel(player,
                    petrolpark.mc.destroy.DestroyMobEffects.FULL_BLADDER.getDelegate(), -1, 0);
                petrolpark.mc.destroy.DestroyAdvancementTrigger.URINATE.award(serverLevel, player);
                serverLevel.setBlockAndUpdate(posOn,
                    petrolpark.mc.destroy.DestroyBlocks.URINE_CAULDRON.getDefaultState());
                state[0] = 0; // reset to avoid retriggering
            }
        }
    }
}
