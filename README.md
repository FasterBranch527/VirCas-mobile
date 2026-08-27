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

## Status
Repository bootstrap includes the app shell, dark gaming theme, bottom navigation, persistent wallet foundation, Room history model, centralized RNG, and initial engines/tests for Dice, Coinflip, Mines and Wheel. Additional games and production polish are being built iteratively.
