# VirCas visual refresh — 8 September 2026

Branch: `astra/vircas-new`. Presentation changes only; this is not a verified release.

## Crash

- Fuel-chamber-anchored ignition, asymmetric gas plumes, rolling fire and cooling smoke.
- 72 stable ember trajectories and seven clipped pieces of the existing rocket artwork, with drag, gravity and tumble.
- Subtle pressure fronts and an irregular thermal edge instead of a dominant neon ring.
- The existing fullscreen coverage, reset-after-confirmed-settlement, input protection and reveal contracts remain intact. Reduced motion uses a quiet fade.

## Blackjack

- Rebuilt the actual `FullBlackjackGameScreen` presentation: deep green felt, brass accents, ivory pip cards, patterned backs, readable scores and chip controls.
- Card entry and hole-card flip; hidden-card accessibility labels do not expose rank or suit.
- Split hands use two columns and wrap to two rows. Long hands wrap instead of squeezing every card into a single tiny fan.
- Scrollable table with pinned portrait controls, landscape table/control columns and keyboard insets.
- Native Compose previews include ready, four split hands and landscape states.
- Retained controller, wager persistence, rules and settlement calls were not refactored.

## Roulette

- Separate fixed bowl/deflectors and rotating rotor, brass dividers, numbered ring, recessed pockets, spindle, shaded ivory ball and speed-dependent trails.
- One absolute-time presentation timeline, independent of game RNG and rendering frame rate.
- Rotor stops at 3.20 s. The ball continues coasting, crosses pockets, is captured at 5.75 s and settles by 6.35 s.
- Final ball position uses the exact renderer's winning pocket center. Reduced motion selects the same final pose without spinning. No result is published early.
- Added six JUnit tests covering all 37 pockets, consecutive spins, continuous phase transitions, dropped frames and reduced-motion equivalence.

## Verification and release gate

- A Python port of the source motion equations was checked across 185 trajectories and 246,790 samples at 30/60/120 fps; maximum float32 landing error was approximately 0.000119 degrees. This was mathematical cross-checking, NOT execution of Kotlin/JUnit.
- Main Blackjack foreground/background color pairs were checked at 4.76:1 or higher for normal text.
- HTML/Canvas reference studies were rendered and inspected for design development. They are NOT screenshots of the Android app and do not establish native layout or performance correctness.
- No local Android SDK/Gradle/device execution was available. The first presentation commit's Android CI failed; a successful build must not be assumed. Inspect the latest PR #11 checks for current status.
- Engine odds, payout calculations, wallet code, signing, CI configuration and dependencies were not changed.

Before release: run `gradle testDebugUnitTest`, `gradle assembleDebug assembleDebugAndroidTest` and `gradle assembleRelease`. Inspect on-device at 320/390 dp and landscape, including large text, four split hands, long hands, hidden-card announcements, IME, reduced motion, consecutive spins, navigation during animation and Crash during scroll/rotation. Verify all existing wallet/round recovery tests. The branch remains WIP until these gates pass.
