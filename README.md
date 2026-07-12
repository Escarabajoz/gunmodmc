# Gun Mod

A Minecraft **Forge** mod that adds a family of hitscan firearms — a pistol, SMG, assault
rifle, shotgun and sniper rifle — each with its own ammo, fire rate, range, bullet spread,
headshot bonus, knockback and muzzle/tracer effects.

Built for the profile shown in the launcher:

| Target            | Value        |
| ----------------- | ------------ |
| Minecraft         | **26.2**     |
| Mod loader        | **Forge**    |
| Forge version     | **65.0.3** (`forge-65.0.3`) |
| Java              | **25** (shipped by Mojang for 26.1+) |
| Build tool        | ForgeGradle 7 / Gradle 9.5 |

---

## Features

- **5 firearms**, each a distinct feel:
  - **Pistol** – reliable sidearm, low spread.
  - **SMG** – very fast fire rate, higher spread, light damage.
  - **Assault Rifle** – balanced all-rounder, good range and accuracy.
  - **Shotgun** – fires **8 pellets** in a wide cone, brutal up close, strong knockback.
  - **Sniper Rifle** – huge single-shot damage, pin-point accuracy, 96-block range, **2× headshots**.
- **Hitscan shooting** – bullets ray-trace instantly from your eyes to the target; no laggy
  projectile entities.
- **Ammo system** – guns consume ammo from your inventory:
  - `Bullet` → pistol, SMG, rifle
  - `Shotgun Shell` → shotgun
  - `Heavy Round` → sniper
- **Headshots** – hitting a living target near eye level multiplies damage.
- **Knockback** – targets are kicked back along the shot line.
- **Fire-rate cooldown** – shown by the vanilla item-cooldown sweep on the hotbar.
- **Feedback** – muzzle flash + smoke, an `end_rod` tracer along the shot, impact particles,
  a dry-fire *click* when you're out of ammo, and layered gunshot sounds (built from vanilla
  sound events, so the mod ships **no audio files**).
- **5 Tsar-class bombs** with escalating fictional yields — 50 MT up to 1 billion megatons —
  each with a beeping fuse, a one-time blast that damages every entity in range with distance
  falloff, an expanding ring **shockwave that erases terrain over several seconds** (a custom
  multi-tick sweep — the vanilla explosion engine can't handle these radii), and a lingering
  particle **mushroom cloud**. Up to 4 simultaneous detonations; bedrock and other unbreakable
  blocks survive.
- A dedicated **Gun Mod** creative tab.
- English and Spanish (`es_es`, `es_mx`) translations.

## How to use

Hold a gun and **right-click** to fire. Keep matching ammo in your inventory. Creative-mode
players have infinite ammo. Fire rate is capped by each gun's cooldown, so the SMG sprays while
the sniper is deliberate.

### Gun stats

| Gun     | Damage | Range | Pellets | Spread | Fire delay | Headshot | Ammo         |
| ------- | ------ | ----- | ------- | ------ | ---------- | -------- | ------------ |
| Pistol  | 5      | 32    | 1       | 1.6°   | 7 ticks    | ×1.5     | Bullet       |
| SMG     | 3.5    | 28    | 1       | 3.2°   | 3 ticks    | ×1.4     | Bullet       |
| Rifle   | 6.5    | 48    | 1       | 1.1°   | 5 ticks    | ×1.6     | Bullet       |
| Shotgun | 3 ×8   | 16    | 8       | 6.0°   | 16 ticks   | ×1.3     | Shell        |
| Sniper  | 14     | 96    | 1       | 0.25°  | 28 ticks   | ×2.0     | Heavy Round  |

*(Damage is in half-hearts. 20 ticks = 1 second.)*

### Bomb stats

Right-click with a bomb to arm it at the block you're aiming at (up to 8 blocks away). The fuse
beeps faster and faster, then: flash, blast, expanding shockwave, mushroom cloud.

| Bomb                                   | Blast radius | Fuse  | Ground-zero damage |
| -------------------------------------- | ------------ | ----- | ------------------ |
| Tsar Bomba (50 Megatons)               | 35 blocks    | 5 s   | 150                |
| Tsar Bomba II (100,000 Megatons)       | 55 blocks    | 6 s   | 300                |
| Tsar Bomba III (1 Million Megatons)    | 75 blocks    | 7 s   | 600                |
| Tsar Bomba IV (100 Million Megatons)   | 100 blocks   | 8 s   | 1,200              |
| Tsar Bomba V (1 Billion Megatons)      | 130 blocks   | 10 s  | 2,500              |

*(Yields are narrative tiers — the radii are hand-tuned so worlds stay playable and servers
stay alive. Entity damage falls off linearly out to 1.5× the blast radius.)*

### Crafting

All recipes use vanilla materials (`I` = iron ingot, `R` = redstone, `S` = stick, `D` = diamond):

```
Pistol         SMG            Rifle          Shotgun        Sniper
 I I            I I I          I I I          I I I          I I I
 I R            I R R          S R I          S R            I R D
                               S    S
```

- **Bullet** ×2 — gunpowder + iron nugget (shapeless)
- **Shotgun Shell** ×2 — 2 gunpowder + copper ingot (shapeless)
- **Heavy Round** ×1 — 2 gunpowder + iron ingot (shapeless)

Bombs escalate — each tier is **8 of the previous tier around an increasingly precious core**:

- **Tsar Bomba** — 8 TNT around an iron block
- **Tsar Bomba II** — 8 Tsar Bombas around a diamond block
- **Tsar Bomba III** — 8 Tier-II bombs around a netherite ingot
- **Tsar Bomba IV** — 8 Tier-III bombs around a nether star
- **Tsar Bomba V** — 8 Tier-IV bombs around a netherite block

---

## Building

You need a JDK 25 (or let Gradle provision one — the Foojay toolchain resolver in
`settings.gradle` handles that automatically).

```bash
./gradlew build
```

The finished mod jar lands in `build/libs/gunmod-1.0.0.jar`. Drop it into the `mods/`
folder of a **Minecraft 26.2 + Forge 65** installation (the profile in the screenshot) and
launch.

To try it in a dev client:

```bash
./gradlew runClient
```

> **Note:** the first build downloads Minecraft 26.2, Forge 65.0.3 and the mappings from the
> Forge Maven, so it needs internet access.

---

## Project layout

```
build.gradle, settings.gradle, gradle.properties   ForgeGradle 7 build (all versions in gradle.properties)
src/main/java/com/gmail/doghash01/gunmod/
  GunMod.java        @Mod entry point, creative tab, registration
  ModItems.java      item registry (3 ammo + 5 guns + 5 bombs)
  ModSounds.java     sound-event registry
  GunType.java       per-gun stat record
  GunItem.java       hitscan firing logic (raytrace, damage, particles, cooldown)
  BombType.java      per-bomb stat record
  NukeBombItem.java  bomb arming (aim, fuse start, consume)
  NukeManager.java   server-tick detonation driver (fuse, blast, ring shockwave, cloud)
src/main/resources/
  META-INF/mods.toml                 mod metadata
  assets/gunmod/items/*.json         client item model definitions (1.21.4+ system)
  assets/gunmod/models/item/*.json   item models
  assets/gunmod/textures/item/*.png  16×16 sprites (hand-authored)
  assets/gunmod/lang/en_us.json      names
  assets/gunmod/sounds.json          gunshot sounds (layered from vanilla events)
  data/gunmod/recipe/*.json          crafting recipes
```

---

## Notes on the target version

Minecraft 26.2 / Forge 65 post-dates the usual tutorials, and its modding API differs from
older Forge (1.20.x) in several important ways that this mod already accounts for:

- **EventBus 7** – the `@Mod` constructor receives an `FMLJavaModLoadingContext`, and
  `DeferredRegister`s are attached with `context.getModBusGroup()`.
- **Item ids** – every `Item.Properties` sets its id via `.setId(ITEMS.key("name"))`.
- **`Item#use`** returns an `InteractionResult` (not `InteractionResultHolder`).
- **Damage** is dealt with `entity.hurtServer(serverLevel, source, amount)`.
- **Client items** – items are rendered through the `assets/gunmod/items/*.json` definitions
  introduced in 1.21.4.

- **`ResourceKey#location`** was renamed to `#identifier` (part of the wider
  `ResourceLocation → Identifier` rename), which `ModSounds` accounts for.

These were verified against the official Forge 26.2 MDK, the Forge docs, and the NeoForged
migration primers. If you ever need to match a slightly different Forge build, one value in
`gradle.properties` controls it:

- `forge_version` (default `65.0.3`) — set it to your installed Forge version.

The `eventbus-validator` annotation processor (a compile-time aid for `@SubscribeEvent`
listeners) is intentionally left commented out in `build.gradle`, because this mod registers no
event-bus listeners. If you add some, uncomment that line and set `eventbus_version`.

## License

MIT — declared in `src/main/resources/META-INF/mods.toml`.
