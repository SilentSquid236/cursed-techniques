# Cursed Techniques

A NeoForge 1.21.1 mod inspired by *Jujutsu Kaisen*: cursed energy, innate techniques, Domain Expansions and Black Flash, with cinematic third-person shots and anime-style impact frames. Built for fights that feel big while staying fair for an average player.

The centerpiece of the **[Cursed Arts](https://github.com/SilentSquid236/cursed-arts)** modpack.

## Techniques

Every player awakens one technique at random on first join.

| Technique | Ability 1 (Z) | Ability 2 (X) | Domain (V) |
|---|---|---|---|
| **Vortex Manipulation** | Collapse Point: a pull point that drags enemies in, then implodes | Repulsion Burst: shockwave that blasts everyone away | **Event Horizon**: everything inside is frozen for 3s, then crawls |
| **Edge Weaving** | Cleave: a 14-block cutting line | Dismantle Storm: 3 waves of slashes in a cone | **Sanctum of a Thousand Edges**: everything inside is cut every second |
| **Ember Calling** | Ember Shot: fast fire bolt (no block griefing) | Eruption: telegraphed eruption under your target | **Coffin of Embers**: constant burning; caster is fire-immune |

- **Cursed energy** regenerates over ~30s. Abilities cost energy and have cooldowns (halved inside your own domain).
- **Domain Expansion** raises a temporary barrier dome (only replaces air, removed afterwards). Overlapping domains **clash**; one shatters. Casting leaves your technique **burnt out** for 10s.
- **Black Flash**: fully charged melee hits have a small chance to deal ~1.8x damage with an impact frame. Landing one puts you *in the zone* for higher odds.

## Cinematics

- Domain Expansion: orbiting third-person shot with letterbox bars and title card.
- Black Flash: side-on shot of both fighters, inverted-color impact frame, screen shake.
- All of it is optional per player: *Mods > Cursed Techniques > Config* (camera shots, impact frames, screen shake, HUD). Impact frames flash; turn them off if flashing lights bother you.

## Commands

| Command | Permission |
|---|---|
| `/cursed info` | everyone |
| `/cursed set <players> <vortex\|blade\|flame\|none>` | op |
| `/cursed reroll <players>` | op |
| `/cursed refill <players> [amount]` | op |

Balance values (damage multiplier, domain radius/duration/cooldown, Black Flash chance, ...) live in `world/serverconfig/cursedtech-server.toml`.

## Building

Requires Java 21 and Gradle 8.14+.

```
gradle build
```

The jar lands in `build/libs/`. Pushing a `v*` tag builds a GitHub release automatically (see `.github/workflows/release.yml`).

## License

MIT. Technique and domain names are original; this is an unofficial fan work not affiliated with the *Jujutsu Kaisen* creators or publishers.
