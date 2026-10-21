# Engram Memory Map — The Fight for Glorton (Android replica)

Portable snapshot of the Engram persistent-memory entries for this project, so the
knowledge survives moving the project folder or switching machines. Engram keys
memory by working directory; the entries below were created under project key
`tfg` (detected from `C:\Dev\Games\TFG`).

## How to re-link after moving the project

- Engram auto-detects the project from the directory where opencode is opened.
  If the folder keeps a name that normalizes to `tfg`, entries re-link automatically.
- Otherwise query/save with an explicit scope: `mem_search({ query, project: "tfg" })`.
- **Gotcha**: if a subfolder with its own `.git` exists (e.g. a `tffg/` clone next
  to the sources), Engram may auto-promote it as a `git_child` project and searches
  return nothing. Always pass `project: "tfg"` explicitly in that case.

## Entry index (project `tfg`)

| ID | Type | Topic key | Date | Title |
|----|------|-----------|------|-------|
| 204 | architecture | — | 2026-10-16 | Análisis tffg.swf — Fighter.as parcial (1-895) |
| 206 | discovery | — | 2026-10-16 | Progreso análisis: clases core leídas, falta loop principal |
| 214 | discovery | `analysis/tffg-source` | 2026-10-19 | Loop principal (frame_51) + las 29 clases completas leídas |
| 215 | bugfix | `android/replica-scaffold` | 2026-10-19 | Crash countdown end: Fighter.world lateinit (5 revisions; upsert history collapsed) |
| 216 | architecture | `port/fighter-core` | 2026-10-19 | Ported Fighter core from AS2 to Kotlin |
| 217 | session_summary | — | 2026-10-19 | Session summary: fighter port |
| 227 | architecture | `port/endurance-mode` | 2026-10-20 | Ported endurance wave mode (v0.3.0) |
| 228 | architecture | `port/player-select` | 2026-10-21 | Ported frame_46 character select (v0.4.0 + v0.5.0) |
| 193–198, 201–203, 208–213 | session_summary | — | 2026-10-16 | Historical progress snapshots of the analysis phase |

Full content of the substantive entries follows. Session summaries are progress
logs; retrieve any of them with `mem_get_observation(id)` when needed.

---

## #214 — Analysis source of truth (all 29 AS2 classes read)

- Main loop lives in `scripts/frame_51/DoAction.as` (not in classes):
  `MainInterval=setInterval(DoMain,25ms)` → `Players[i].DoFighter()` +
  `Computers[i].DoAI()` + `VCam.DoCamera()` + `OnOSD.DoOSD()`;
  `TimerFight=setInterval(FightTimer,1000)` → countdown 5..GO, GameTime, time limit.
  `InitArea()` spawns players at `WorldMC.SpawnP1..P4`, creates OSD, VCam 600x400.
- frame_51: game types endurance/menugame/tutorial; LimitMode stock (Lives=LimitVal)
  vs time (countdown); pause key P (Key 80); per-stage bounds (Rooftop/Mogadishu/
  B52/Space with BoundsCam and Bounds rects); `ItemGen.init([Mine,Grenade],
  Settings.ItemFrequency)`.
- frame_42: boot/global — P1 keys WASD+J/K/Shift, P2 arrows+numpad;
  Settings{Quality, ItemFrequency:3, Mute, BestEndurance} + Keys persisted in
  SharedObject; `Snd.AddSound` table for 20+ sounds; `Game.Type=menugame` at boot
  (menu IA-vs-IA demo).
- frame_46: fighter + color selection (SetColor avoids duplicates);
  `Fighters=[SBL,Peach,Trash,Coffee,Default,Aubergine]`, `Colors=[red,blue,green,orange]`;
  frame_49: results (Rank/KOs/Deaths/Score or endurance Kills/Time);
  frame_170: GameEnd ranking; frame_48: stage select; frames 100/101/143:
  transitions; frame_47: keyconfig UI; frame_40: back to Intro; frame_2: stop().
- AI (AIControl): `DoAI()` picks a victim each tick (50% prob), MoveTo + Attack
  with a timed ActionQ (`1/Level*500+random(200)` ms); higher Level = more aggressive.
- Combat: punches 5 dmg (Throw 3 @ 45°), punchUp/Air 10 dmg (Throw 6 @ 90/80°),
  backthrow 15 dmg; Explosion affects by distance (SquareSize = size*10²+400),
  damage Size*5, Throw Size*2.
- Projectiles: Bullet(xInc 20, dmg 10), EnergyBall(xInc 5, dmg 7 + electrocute
  100ms), Rocket(gravity 0.5, explosion size 5), Pencil(dmg 20), Poop(dmg 15),
  Garbage(dmg 6). Items: Mine(dmg 20, arms 1s after sticking), Grenade(explodes on
  impact, size 4), Star(invulnerable 9s).
- Fighter stats `super(a,b,c)`: Default BallLock(0.5,0.5,0.5) electric EnergyBall;
  Aubergine(0.5,0.6,0.4) Pencil; Coffee(0.4,0.7,0.4) Poop; Peach(0.4,0.5,0.6)
  Bullet+Rocket; SBL Strawberry(0.5,0.5,0.5) melee Kamehameha scaled damage;
  Trash(0.6,0.4,0.5) Garbage; RedGuy is a non-playable AI enemy.
- VCamera: united rect of FocusObjs +100px margin, lerp /5 per tick, zoom to
  600:400 ratio, shake cos/sin 1s, StillObjects = HUD anchored to screen.
- ItemGen spawns at SpawnH1..H4 with prob `random(len+50/freq)` every `10000/freq` ms.

## #206 — Analysis progress snapshot

- `The fight for glroton/scripts/__Packages/` — COMPLETE: Fighter.as (1071 lines),
  World.as (256), PlayerControl.as (362), OSD.as (215 HUD/damage/lives/timer),
  Projectile.as (44), KeyCombiKey.as (19); PARTIAL: Snd.as. symbols.csv complete
  (115 lines, character/projectile/audio IDs).
- The 16 files in `%3Cdefault package%3E\` are `Object.registerClass` STUBS — the
  main loop is NOT there; candidates were timeline scripts (solved in #214).
- Read tool truncates ~50 lines despite limit; use growing offsets.

## #204 — Early analysis (symbolClass IDs, World/PlayerControl)

- symbolClass IDs: characters DefaultPlayer=685, RedGuy=705, AuberginePlayer=115,
  SBLPlayer=208, CoffeePlayer=302, TrashPlayer=409, PeachPlayer=544; projectiles
  Bullet=489, Rocket=432, Mine=61, Grenade=63, Pencil=77, Star=9, EnergyBall=600,
  Poop=266, Garbage=368; utilities OSD=730, CameraTrick=787, Shield=852,
  ControlSetter=860, LoadingMC=826, MenuInitMC=911; stages Rooftop=726,
  Mogadishu=832, B52=873, Space=886, Tutorial=907, Spawn1=559, Spawn2=547.
- World.as: arrays Fixed/Moving/HitCheck/Players/AIHelp/Items/Booms/Killers;
  CollisionDetect(MC,Basic,Platforms,Helpers,MovingC,LowRes) with
  "boom"→Explosion, "killer"→_x+=30, borders Left/Right/BC/Bottom/Top.
- Input: keydown/keyup/keyhold handlers per PlayerControl.C; combos in
  PlayerControl.C (B/U/D/L/R/S + index); Trc.Up1/Up2/Up3 tap-vs-hold with
  PlayerControl.Reflex threshold.

## #216 — Fighter port (architecture, subagent)

- Ported `Fighter.as` + `DefaultPlayer.as` to
  `core/engine/.../fighter/{Fighter,FighterStats,DefaultPlayer}.kt`; Fighter
  abstract; state/animation split (`Fighter.state` = AS2 State string,
  `Fighter.animation` = gotoAndStop label), both enum `FighterState`; attack
  gating via `attackTimeMs` accumulator (TICK_MS per tick, MS_PER_ANIM_FRAME
  mapping, duration table in `Fighter.companion.attackDurationMs`).
- Discoveries: `GameConfig.GRAVITY=0.6` is Item.as's value — Fighter.as Gravitron
  uses 0.5*Gravity; Fighter.as:1054 reads `InfObj.type` but World.as only sets
  `objType` → thrown-bounce dead branch ported behind
  `THROWN_BOUNCE_ENABLED=false`; AS2 `Die` sets Dead=true even when respawning —
  the engine must keep ticking dead fighters; `specUpOk` defaults false (AS2
  undefined blocks up-special until first landing); AS2 Animate overwrites the
  takingHit pose with locomotion — the electrocuted pose survives only because
  State=electrocuted matches no ground branch or via the THROWN early-return.

## #215 — Bugfix log (topic `android/replica-scaffold`, 5 revisions collapsed)

- Current body: crash at countdown end — `Fighter.world` lateinit never assigned;
  fixed in `GameFactory` (assign before `init()`, also propagate `limitMode`).
- The same topic also recorded (earlier revisions, collapsed by upsert): the
  audio pipeline (24 exported sounds to `app/src/main/assets/sounds/`, FLV/ADPCM
  limitation for Fart/PeachRocket), `AndroidSoundSink` (SoundPool SFX with
  variant arrays like Snd.AddSound, MediaPlayer looped music), stage music wired
  in GameFactory, and the first green `assembleDebug` + its fixes (local.properties
  with forward slashes, missing GlortonApp/withTransform imports, TouchButton
  package/receiver rewrite, `DefaultPlayer.spawnProjectile` override bridging to
  the concrete `EnergyBall`).

## #217 — Fighter port session summary (assumptions inventory)

- Invented durations (multiples of 25ms, **need playtest validation**): punches
  350/400, koAttack 400, specialGround/Air 1000, specialUp 800,
  specialBackThrow 650, dodge 450; hand offsets for EnergyBall spawn 18/-10/-40
  (only -10 comes from AS2); hitbox AABB 44x64 feet-anchored; `gameOn` default
  true; death order via companion counter + `Fighter.resetDeathOrder()`.

---

## #228 — frame_46 character select (analysis + port, v0.4.0/v0.5.0)

- Real `Game.Type` ids: `oneonone | quick | vsmode | endurance | menugame` —
  the port's invented `"versus"` is gone; constants live in
  `com.glorton.engine.select.GameType`.
- The four mode buttons define the slot layout: 1011 `oneonone` NumGamers 1 /
  NumPlayers 2 / `Rand.CP 1`; 1016 `quick` 1 / 4 / `Rand.CP 3`; 1022 `vsmode`
  4 / 4 (`Game.Rand` undefined); 1006 `endurance` 1 / 1.
- frame_46 geometry: FighterBox `x = i*94+6, y = 70`; PlayerPoseBox
  `x = i*(550/N)+25, y = 188, Team = Colors[i]`; PlayerCoin `x = i*32+30,
  y = 50` frame `Colors[i]`; every slot starts `Level = 7`; CPU slots roll
  `Fighters[random(n)]` only when `Game.Rand != undefined`.
- `SelectFighter` stores the fighter and then runs `SetColor(slot, 0)`: it skips
  any color another player already holds **with the same fighter** (different
  fighters may both be red). `Team` comes from the slot *position*, `Colr` is
  derived — they can differ.
- `gotoStageSelect` drops slots that never picked, but pushes `Team` from the
  *original* slot index; frame_51 then compacts the enabled ones. An unpicked P1
  therefore shifts the CPU into `Players[0]` while it keeps team `blue`.
- AISetter = DefineButton2_804 (`-`) / 805 (`+`), clamp 1..20; PlayerPoseBox
  Toggle cycles P -> CP -> Disabled but bails out when `Game.Mode == "single"`
  (the only mode this port drives).
- Port deviations: `CharacterSelect.armedSlot` replaces dragging PlayerCoin over
  a FighterBox (touch has no cursor), and `confirm()` returns null instead of
  navigating into a match with an empty roster (AS2 gates on *picked* slots).
- Gotcha that broke the first test run: in `oneonone` the CPU roll pre-claims
  `red` with `SBLPlayer`, so P1's SetColor starts at `blue` — color tests must
  use `vsmode` (no `Game.Rand`) or inject another roll.

---

## Integration decisions made after #216 (not stored in Engram individually)

- `ThrowSource` interface (`fighter/ThrowSource.kt`, weight only): `Fighter`
  implements it and `doThrow` accepts `ThrowSource?` because EnergyBall.as throws
  *the ball itself* (`Throw(2.5, 45*dir, this)`); `lastSender` upgrades only for
  real fighters (AS2 would corrupt kill credit to NaN).
- `Fighter.damage/doThrow` 4th param kept as `fromExplosion` (maps to AS2 `Force`).
- `Explosion.kt`: damage window = BoomMatter 7 transitions @30fps (~233ms), life
  = BoomWave 20 transitions (~667ms), `Shake(-500)` on first tick via `World.camera`.
- `World.camera: VCamera?` wired by GameFactory (explosion shake).
- `RectF.intersects` is **inclusive**: AS2 has no AABB gate and its point hitTests
  include boundary points (a fighter resting exactly on a platform top still touches).
- `Item.setState` uses `@JvmName("setStateRule")` to dodge the JVM clash with the
  `state` property setter while keeping the AS2 spelling.
- Rocket.as truth: rockets fly *through* platforms; the Explosion(5) is created
  only on fighter hit (not on wall/life end).
- `GameFactory` must assign `fighter.world = world` before `init()` — the only
  `lateinit` in the engine (grep `lateinit` to audit wiring).

## Known pending items

- User-reported controller/touch issues (details pending) — next session topic.
- Rooftop platform geometry in `RooftopStage.kt` is an approximation; real
  geometry lives in the SWF sprite `DefineSprite_726_Rooftop` (SpawnH1..4 item
  bars are approximations too, see `RooftopStage.itemSpawns`).
- Sounds `Fart.wav` and `PeachRocket.wav` exported by JPEXS as Flash-ADPCM FLV,
  not convertible here (no ffmpeg available); Fart falls back to Fart2, and
  "Rocket" (Peach's up-special, now ported) still plays silently in
  AndroidSoundSink — needs a converted asset.
- Star is ported but **dormant**: no decompiled script spawns it
  (`frame_51` passes only `["Mine","Grenade"]` to ItemGen, no
  `attachMovie("Star")` exists) — investigate the SWF timeline actions.
- `ItemIndicator` (AS2 root-level marker above items, synced to visibility)
  is not ported — renderer has no equivalent; visual aid only.
- Menus: the mode menu and the frame_46 player select are ported (v0.4.0
  model, v0.5.0 screen); still missing are stage select (frame_47/48) and the
  menugame/tutorial attract flows. The stage is fixed to Rooftop.
- `vsmode` (4 humans) is listed on the menu but its PLAY button is disabled: the
  app feeds a single touch input into `Engine.tick`, which indexes `inputs` by
  **fighter slot** (every reachable mode keeps the one human in slot 0, so both
  indexings agree today), while AS2 binds keys per human (`Keys[P]`). That
  mapping has to become "human ordinal" before local multiplayer works.
- Sprite extraction (fighter-select poses, results podium), WiFi LAN
  multiplayer.
- Endurance assumes frame_51's decompiled truth; the SWF timeline actions that
  could spawn the dormant Star item still need checking.
