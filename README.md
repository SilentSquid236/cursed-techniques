# Cursed Techniques

A NeoForge 1.21.1 mod inspired by *Jujutsu Kaisen*: cursed energy, innate techniques, Domain Expansions and Black Flash, with cinematic third-person shots and anime-style impact frames. Built for fights that feel big while staying fair for an average player.

The centerpiece of the **[Cursed Arts](https://github.com/SilentSquid236/cursed-arts)** modpack.

## Grades

Every player awakens on first join with a technique and a grade. Most start at **Grade 2**; a few are born stronger.

| Grade | How you get it | Unlocks | Stats |
|---|---|---|---|
| **Grade 2** | ~95% of new players | Abilities 1 and 2 | 100 CE, base damage |
| **Semi-Grade 1** | 2,500 XP | Bigger reserves | 130 CE, +10% damage |
| **Grade 1** | ~4% of new players, or 8,000 XP + 3 Black Flashes | Domain Expansion | 170 CE, +25% damage |
| **Special Grade** | ~1% of new players, or 30,000 XP + 12 Black Flashes + 2 boss kills | Maximum technique, Reverse Cursed Technique | 250 CE, +50% damage |

**Grade XP** comes from fights: hostile kills (about max health / 4), bosses (max health x 2), player kills (40, once per victim per 10 min), Black Flashes (30) and domains that catch someone (20). Farm animals and villagers give nothing. Promotions are announced to the server. Check progress with `/cursed info`.

**Reverse Cursed Technique** (Special Grade): you slowly heal by burning cursed energy.

### Rare innate techniques

The ~1% of players who awaken as a Special Grade receive a technique nobody else can get:

- **Unbounded**: Collapse Point and Repulsion Burst at higher power, plus passive **Infinity**, which blocks 60% of incoming damage while you have energy (sure-hits inside an enemy domain pierce it). Domain: **Unbounded Void** (larger, freezes for 5s). Maximum: **Hollow Annihilation**.
- **Calamity Sovereign**: Cleave and Dismantle Storm at higher power. Domain: **Shrine of Calamity**, an *open* domain with no barrier but 1.5x the reach. Maximum: **Divine Flame**.

## Techniques

| Technique | Ability 1 (Z) | Ability 2 (X) | Domain (V, Grade 1+) | Maximum (G, Special Grade) |
|---|---|---|---|---|
| **Vortex Manipulation** | Collapse Point: a pull point that drags enemies in, then implodes | Repulsion Burst: shockwave that blasts everyone away | **Event Horizon**: everything inside is frozen for 3s, then crawls | Gravity Well |
| **Edge Weaving** | Cleave: a 14-block cutting line | Dismantle Storm: 3 waves of slashes in a cone | **Sanctum of a Thousand Edges**: everything inside is cut every second | World Rend |
| **Ember Calling** | Ember Shot: fast fire bolt (no block griefing) | Eruption: telegraphed eruption under your target | **Coffin of Embers**: constant burning; caster is fire-immune | Meteor |

- **Cursed energy** regenerates over ~30s. Abilities cost energy and have cooldowns (halved inside your own domain).
- **Domain Expansion** raises a temporary barrier dome (only replaces air, removed afterwards). Overlapping domains **clash**; one shatters. Casting leaves your technique **burnt out** for 10s.
- **Black Flash**: fully charged melee hits have a small chance to deal ~1.8x damage with an impact frame. Landing one puts you *in the zone* for higher odds.

## Cinematics

- Domain Expansion: orbiting third-person shot with letterbox bars and title card.
- Black Flash: side-on shot of both fighters, inverted-color impact frame, screen shake.
- All of it is optional per player: *Mods > Cursed Techniques > Config* (camera shots, impact frames, screen shake, HUD). Impact frames flash; turn them off if flashing lights bother you.

## Claims and protection

Techniques play by the same rules as normal attacks. Knockback, pulls, burning, freezes and other effects only land on targets the caster was actually allowed to damage, so claim mods (Open Parties and Claims, FTB Chunks, ...) that block PvP or entity damage block techniques and domains too. Domain barriers fire a normal block-place check per chunk and skip protected land.

## Commands

| Command | Permission |
|---|---|
| `/cursed info` | everyone |
| `/cursed set <players> <vortex\|blade\|flame\|none>` | op |
| `/cursed reroll <players>` | op |
| `/cursed refill <players> [amount]` | op |
| `/cursed grade <players> <grade_2\|semi_grade_1\|grade_1\|special>` | op |
| `/cursed xp <players> <amount>` | op |

Balance values (damage multiplier, domain radius/duration/cooldown, Black Flash chance, spawn odds, XP thresholds, ...) live in `world/serverconfig/cursedtech-server.toml`.

## Building

Requires Java 21 and Gradle 8.14+.

```
gradle build
```

The jar lands in `build/libs/`. To publish, bump `mod_version` in `gradle.properties` and push to `main`: GitHub Actions builds the jar, creates the `v<version>` release and uploads to CurseForge (see `.github/workflows/release.yml`).

## License

MIT. Technique and domain names are original; this is an unofficial fan work not affiliated with the *Jujutsu Kaisen* creators or publishers.
