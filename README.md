# VirCas Mobile

VirCas is an offline-only virtual gaming hub for Android. All coins, items, odds, teams and events are fictional and have no monetary value. There are no deposits, purchases, withdrawals, crypto, cash-out to real money, or integrations with real bookmakers/casinos.

## Stack
Kotlin · Jetpack Compose · Material 3 · Navigation Compose · Room · DataStore · Coroutines/Flow

## Development
- JDK 17
- Android SDK 35
- Gradle 8.11.1 (CI installs it directly; a binary wrapper is intentionally not committed through the API bootstrap)

Build:
```bash
gradle clean testDebugUnitTest assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Round-stability work in progress

Work continues on `astra/vircas-new`, not on `main`.

- [Latest continuation: confirmed round-write queue, recovery gate and verification limits](docs/ROUND_WRITE_QUEUE_UPDATE.md)
- [Original implementation handoff, remaining P0/P1 work and acceptance matrix](docs/ROUND_STABILITY_HANDOFF.md)
- [Draft PR #11](https://github.com/FasterBranch527/VirCas-mobile/pull/11)

The branch includes the transactional wager ledger and retained-state groundwork. The latest continuation connects the recovery/error gate and makes failed checkpoint acknowledgements wait for successful retry without letting settlement overtake them. Remaining game-screen integrations, migration/process-death testing and actual Android CI results are still required. **This branch is not a verified release.**

Gameplay probabilities, prices, signing keys and penny physics are unchanged by this stabilization work.
