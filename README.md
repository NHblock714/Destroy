# Destroy (1.21.1 NeoForge)

Unofficial community port of [Petrolpark/Destroy](https://github.com/Petrolpark-Mods/Destroy)
from Forge 1.20.1 to NeoForge 1.21.1.

> Authorised non-official port. Original mod by Petrolpark.

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.200+ (21.1.219+ recommended at runtime) |
| Java | 21 |

## Dependencies

### Required

- **Create** — 6.0.10
- **petrolpark library** — 1.4.31+ (1.4.32+ recommended at runtime)
- **Ponder** — bundled via Create
- **Catnip** — bundled via Create
- **Registrate** — `MC1.21-1.3.0+67`
- **Flywheel** — `1.0.5`

### Optional

- **JEI** 19.25.0.321+ — recipe browser integration. **Now optional** in this port: the JEI plugin is gated by `Mods.JEI.isLoading()` and the mod runs without it.
- **Curios** — gas mask / lab coat slot bindings.
- **Create: Big Cannons** — `custom_explosive_mix` and friends usable as cannon shells with propellant-blob effects.
- **Create: Connected** — `FluidVessel` integration with Destroy's mixture-aware fluid network.
- **Farmer's Delight** — tag compatibility (`c:foods/raw_porkchop` etc.).

## What's different from 1.20.1 upstream

This is a port; gameplay behaviour is preserved 1:1 wherever the underlying API permits.
The list below groups the visible deltas into three categories — behaviour adjustments, fixed bugs, and net-new features.

### Behaviour changes

Tweaks to existing mechanics. Most are user-visible.

- **Vat inventory: 1 → 9 slots.** Multi-catalyst reactions (e.g. zinc + copper + … paths) now work; the upstream `SmartInventory(9, this)` was stubbed to one slot during early port and is now restored.
- **Concentration tooltip precision: 1 → 3 decimal digits.** Mixture displays show `0.300 M` instead of `0.3 M`; the M / mM / μM unit-prefix cutoff shifts to 10⁻³ so values stop flickering near the boundary. Temperature line still 1 digit.
- **Chemical-poison damage no longer knocks back.** `alcohol`, `baby_blue_overdose`, `chemical_burn`, `chemical_poison`, `headache` joined the vanilla `no_knockback` damage tag, matching vanilla's magic-damage convention.
- **JEI U/R reverse lookup deduplicates matches and is subclass-aware.** Electrolysis / Mixing / Arc Furnace recipes (BasinRecipe subclasses) now show up under molecule reverse-lookups; reaction hits are deduplicated; the reverse-lookup plugin only supplements JEI's static index for cross-side matches so the same reaction doesn't show twice.
- **JEI datapack-reaction registration deduplicated.** Datapack reactions no longer appear twice after a `/jei reload` or resource-pack switch.
- **Distillation output temperatures quantised to 0.1 K.** Floating-point drift no longer prevents cross-mod tanks from stacking the resulting Mixture FluidStacks.
- **Blowpipe extracts fluid on left-click.** Matches Test Tube / Beaker / Cylinder UX; upstream supported right-click drain only.
- **Measuring Cylinder max stack size: 64 → 1.** Aligns with the other glass containers; upstream was already `stacksTo(1)`, the port silently dropped it.
- **Vat side block: Flask + vanilla water → MixtureConversionRecipe** triggers from the side as well as from a pump. Both paths now respect the conversion recipe.
- **Pollutometer is a valid Display Link source.** Selectable pollution-type readout (percentage or progress-bar style).
- **Colourimeter generalised.** Works against any tank Block Entity exposing a fluid handler + glass window, not only Vat side blocks.
- **JEI is now optional.** The mod runs without JEI; the plugin is gated by `Mods.JEI.isLoading()` and lazy-loads only when JEI is present.
- **First-join fork-welcome chat message** with clickable links to the upstream Discord and this fork's GitHub. Toggleable via the `ForkWelcomeMessage` client config.

### Bug fixes

#### Inherited from upstream 1.20.1

- **Centrifuge ion-pair charge balance** — counterion-moles formula had numerator and denominator swapped; 1:1 salts hid it, 3-charge species (Fe³⁺ / Cl⁻ in 1:3) exposed the 3:1 inversion.
- **Centrifuge gas/liquid phase sort** — trace gas-phase species were ranked as "densest" by a sort key that mixed mixture-wide concentration with phase-specific volume; the gas pair now ranks by post-separation gas density and lands in the light tank.
- **Round-Bottomed Flask / Beaker / Test Tube pour-out lost fluid** — `tryEmpty` drained the source first, then filled the destination; if the destination couldn't accept the full amount, the surplus disappeared. Now sim-then-execute: only the amount that fits is drained.
- **CBC `custom_explosive_mix_shell` fuze install** — overriding `getFuze() → getItem(0)` silently lost the fuze on install/drop/render; CBC stores fuzes in a data component, not a numbered slot. Override removed.
- **JEI item-side reverse-reaction lookup no-op** — missing `return` in an `anyMatch` lambda made the reverse lookup never match.
- **JEI reaction recipe ClassCastException** — `ReactionRecipe` was unchecked-cast straight to `RecipeHolder<R>`; now wrapped properly.
- **Achievement chain stalled after dichromate stub** — advancement listeners were stubbed; rewired so the late-game advancement chain unlocks again.
- **Molecule-tag search in JEI** (`#carcinogen` etc.) — `MoleculeJEIIngredient` missed the `getTagStream` override; restored along with the deprecated `getTooltip` overload so JEI's text index picks up tag rows.
- **Hazmat Helmet config crash at reload** — `getMaxDamage()` resolved config before it was loaded; falls back via `safeInt` now.
- **Pumpjack output emitted flowing fluid** instead of source — Registrate `.getSource()` fix.
- **Mixed-explosive primed entity rendered as white square** (asymmetric `SuperByteBuffer.center()` between base and label).
- **Mixed-explosive blockstate typo** (`cullface: "dpwn"`).
- **`cordite_rods` missing from creative tab** — registry-id drift from `cordite`.
- **`urine_cauldron` mined into `minecraft:air`** instead of dropping a cauldron — loot entry fix.

#### Surfaced by 1.21's stricter APIs (or introduced during the port and caught in series)

- **`/reload` crash in worlds without a Vat** — `BlockIngredient` types registered eagerly in `FMLCommonSetupEvent`.
- **Dedicated-server class-load crashes** — seven+ blocks/items hard-referenced `ClientLevel` / `Screen` classes through the new RuntimeDistCleaner contract; refs moved into nested `@OnlyIn(Dist.CLIENT)` handlers.
- **Player kicked when urinating into a cauldron** — server tick was touching Create's `FluidFX` (which references `ClientLevel`).
- **Ponder `StitchedSprite.ALL` concurrent-init crash** — multiple Create-addon mods raced on the same `HashMap` during `<clinit>`; mixin promotes it to a `ConcurrentHashMap`. Saves the entire Create ecosystem in addon-heavy packs.
- **`PollutionNumberProvider` chunk-branch NPE** — Alex's Mobs `ShoebillAIFish` hand-builds a `LootParams` without `ORIGIN`; pollution helper now tolerates the missing param.
- **`LegacyMixture.readNBT` unknown-molecule NPE** — datapack reload order could place molecule registration after `RecipeManager.apply`; unknown molecule entries are WARN-skipped instead of aborting the reload.
- **`FogHandler` config-not-loaded crash on title screen** — `isPollutionEnabled()` defensively returns `false` until server config is available.
- **JEI tooltip drift on molecule hover** — Catnip `AbstractSimiWidget` missed `afterRender`; PoseStack push/pop balanced.
- **JEI first-open freeze** — Pump now reads recipes via `RecipesUpdatedEvent` instead of a constructor-time call.
- **JEI molecule input/output map duplicate accumulation across reloads** — `JeiProcessingRecipeMixin` static map cleared via `RecipesUpdatedEvent`.
- **Crying tear particles invisible in multiplayer** — `TearParticle.Data` lacked `equals` / `hashCode`, so vanilla `StreamCodec.unit` silently dropped packets; particles now broadcast via `ServerLevel.sendParticles`.
- **13 `petrolpark:config_boolean` data files** migrated to the renamed `petrolpark:config_bool` codec — alcohol-disabled mode actually blocks moonshine now.
- **9 broken recipes** — id drift / FD tag renames / Distillation fireworks reshuffling.
- **Six ore loot tables** migrated to the 1.21 `match_tool.predicates.minecraft:enchantments` schema (silk touch was dropping the block itself).
- **Distillation output temperatures drifting ~10⁻⁴ K per tick** — quantised so cross-mod tanks can stack the Mixture FluidStacks.
- **`ItemMixtureTank.fill` did not merge mixtures** on Flask / Cylinder / Test Tube fill paths.
- **Vat cooling lockup** after a v0.3.0 `whenFluidUpdates` callback regression — reverted and replaced with explicit refresh points so heat exchange stays unlocked even after long extraction sessions.
- **Sub-mB liquid condensate ghost-leak** — `VatFluidTankBehaviour` rounded sub-0.5 mB liquid output to 0 mB and the moles vanished; now folded back into the gas combine so the next tick can accumulate.
- **Creative Pump network refresh chain** — `notifyMultiUpdated` propagation, `onSpeedChanged → updatePressureChange`, and stale `FluidNetwork.targets` all rewired.
- **Tree Tap × Sable mixin clash** — Sable's `@Redirect` on `getBreakingPos()` bypassed TreeTap's subclass override; parent class changed to plain `KineticBlockEntity` and the breaking loop inlined. Also fixes Tree Tap permanently stuck after a stop+resume stress cycle.
- **Centrifuge two-output behaviour type collision** — `denseOutputTank` and `lightOutputTank` shared `BehaviourType.OUTPUT` and overwrote each other's NBT; dense gets its own type, old worlds remain compatible.
- **BubbleCap render perf** — VoxelShape rebuild thrash and per-frame fluid → fluid-type → light-level lookup eliminated via precomputed shape table and `whenFluidUpdates`-driven luminosity cache.
- **Cooler captured by Mechanical Bearing leaked liquid air ex nihilo** — virtual-state propagation tightened.
- **Blowpipe first-person glass orientation**.

### New features (additions vs upstream 1.20.1)

- **EMI plugin** — native Reaction / Generic Reaction categories with the same molecule rendering JEI users have, molecule search index, structure-rendering tooltips. Generic reactions get unique recipe ids from `GenericReaction.id` so EMI doesn't dedup them all to a single tile. Register call fully `try/catch`-guarded.
- **Data-driven chemistry** — datapacks can now register custom chemistry:
  - **Elements** — `data/<ns>/destroy/elements/<id>.json` adds symbol / mass / electronegativity / valencies (optional VSEPR override).
  - **Molecules** — `data/<ns>/destroy/molecules/<id>.json` defines FROWNS structure + physical properties + tags.
  - **Reactions** — `data/<ns>/destroy/reactions/<id>.json` defines reactants / products / catalysts / item reactants / kinetics / requires-UV / result.
  - Custom 3D atom textures via resource pack `assets/<ns>/models/chemistry/atom/<id>.json`.
  - S2C sync to clients on reload + per-player resync on join via `OnDatapackSyncEvent`.
  - Load order is fixed elements → molecules → reactions, so cross-datapack references resolve cleanly.
  - Tutorial packs at `datapack_en/` and `datapack_zh/` cover all three layers + a matching resource pack.
- **Reversible reactions in the datapack** — `ReactionDefinition.reverse` field accepts an optional kinetics block + optional result; auto-derives Hess-Law-consistent activation energy / enthalpy when omitted.
- **JEI molecule drill-down** — "which recipes consume molecule X" / "which recipes produce a Mixture containing X" via `MixtureFluidIngredient.getReferencedMolecules`.
- **Display Link → Pollutometer**.
- **Generalised Colourimeter** — see Behaviour changes above.
- **Create: Big Cannons** compat — `custom_explosive_mix` and friends register as CBC munitions with custom propellant-blob effects.
- **Create: Connected** compat — `FluidVessel*Mixin` makes Connected's fluid vessels mixture-aware so they cooperate with Destroy's pipe network for in-place mixture merging.
- **Smog atmospheric tinting** — `FogHandler` + `SmogAffectedBlockColor` re-port from upstream; grass / leaves / water / sugarcane / pink petals visibly darken as the chunk's smog level rises.
- **Five missing achievements wired** — `SHOOT_HEFTY_BEETROOT`, `CUT_ONIONS`, `FIREPROOF_FLINT_AND_STEEL`, `HABER_PROCESS`, `STEAM_REFORMATION`; `USE_KEYPUNCH` trigger id corrected (`keypunch_use` → `keypunch`).
- **`vat_interaction` Ponder scene fully ported** — including `en_us` / `zh_cn` translations (upstream shipped only `en_gb` / `pl_pl`).
- **NeoForge tag namespace migration** — 21 datapack tag files moved from `forge:` to `neoforge:`.
- **Spout filling non-fluid-handler items** path restored (Fire Retardant spray fires correctly).
- **CircuitPatternIngredient** ingredient type eagerly registered so Colourimeter / Pollutometer / Redstone Programmer recipes load reliably.

## Building

```bash
./gradlew jar
```

Produces `build/libs/destroy-1.21.1-0.3.7.jar` (about 9 MB).

> Build requires a local `_Migration-Toolkit-1.21/` directory as a sibling of
> the project root, containing the petrolpark library Maven layout and the
> compat-only jars referenced by `build.gradle`. End users who just want to
> run the mod can install the jar directly into a NeoForge instance and do
> not need the toolkit.

## Modpack usage

You are welcome to include this fork in modpacks. Two requirements:

1. **Credit the fork in the modpack description.** Include a link to this repository — `https://github.com/NHblock714/Destroy/tree/1.21.1-neo` — alongside the credit for the upstream Petrolpark mod. The wording is up to you; the intent is that anyone installing the pack can find this port if they hit a port-specific bug.
2. **Do not redistribute the jar as if it were the original 1.20.1 Destroy.** This is an unofficial port; bugs introduced by the port should not be reported to Petrolpark.

Beyond that there are no extra rules — no payment, no permission request, no name-change required. Just keep the link visible so testers know where to file issues.

## Acknowledgements

- **[Petrolpark](https://github.com/Petrolpark-Mods)** — original mod author. This port is unofficial but authorised via Discord.
- **petrolpark library** — the upstream library that this port depends on for Registrate extensions, ponder helpers, ingredient infrastructure and more.
- **The Create team** — for Create itself and the conventions this mod builds on.
- **Catnip & Ponder** — for the rendering helpers and in-game scene system that Destroy uses for its guides.
- Maintainers of **Sable**, **Create: Connected**, **Create: Big Cannons**, **Curios** and **Farmer's Delight** — for the integration surfaces and for help reproducing edge cases during compat debugging.

## License

All Rights Reserved (mirrors the upstream Destroy license).

---

**Maintainer**: [NHblock714](https://github.com/NHblock714)  
**Upstream**: [Petrolpark-Mods/Destroy](https://github.com/Petrolpark-Mods/Destroy)
