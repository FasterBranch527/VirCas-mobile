# VirCas macOS port

Branch: `port/macos-desktop`  
Base: `astra/vircas-new`

## Architecture

The Android application remains unchanged in `:app`.

The macOS client is isolated in `:desktopApp` and uses Compose Desktop:
- native desktop window and sidebar navigation;
- 13 game entry points;
- local virtual wallet;
- local round history;
- desktop settings;
- local persistence in `~/.vircas`;
- DMG packaging through Compose Desktop native distributions.

## Android-only replacements

The following Android-specific pieces are not reused directly on macOS:
- `Application` / `AndroidViewModel`;
- Android Room construction;
- Android DataStore delegates;
- SceneView.

The desktop target uses JVM/Compose equivalents and its own local persistence layer so it can run without Android runtime dependencies.

## Commands

Run:
```bash
gradle :desktopApp:run
```

Compile:
```bash
gradle :desktopApp:compileKotlin
```

Build DMG:
```bash
gradle :desktopApp:packageDmg
```

Expected DMG output:
`desktopApp/build/compose/binaries/main/dmg/`

## Compatibility

The port is additive. It does not migrate or overwrite Android data and does not change the Android application ID, storage, signing configuration or gameplay source tree.
