# Magic Brush

An offline Android drawing puzzle for ages and skill levels that enjoy creative physics. Lead the explorer to a portal across 60 authored levels in six worlds. Paint bridges, ramps and shields, or close a loop to create a falling weight for a pressure plate.

| Release | Configuration |
|---|---|
| Version | 1.0.0 (1) |
| Application ID | `com.magicbrush.brush` |
| SDK | Android 8.0 / API 26 minimum, API 36 compile and target |
| Game engine | Kotlin, Compose, hardware Canvas, 120 Hz fixed-step puzzle physics |
| Progress | Local DataStore, 180 stars, independent offline music and sound settings |

## Controls

- Draw by dragging a finger or the primary mouse button. Lift to commit a stroke.
- Use **Go** to start the explorer. You may still draw during the attempt. **Undo** restores the ink from the latest stroke.
- Use **Tab / Shift+Tab** and **Enter** for menus. In a puzzle the arrows move the drawing cursor. Press **Space** to begin or finish the stroke, **G** to go, **P** or **Esc** to pause, **Z** to undo and **R** to restart.
- The terrain uses the same coordinates in portrait, landscape and resizable windows. The HUD respects display cutouts.

## Build

Install JDK 17, Android SDK Platform 36 and Build Tools 36.0.0. Point `ANDROID_HOME` at the SDK or use Android Studio to create `local.properties`.

```sh
./gradlew lintRelease testReleaseUnitTest assembleDebug
```

To produce signed release packages, set the following environment variables in your local shell or a protected CI runner. Use the private PKCS#12 and credentials delivered to the repository owner.

| Environment variable | Value |
|---|---|
| `BRUSH_SIGNING_STORE` | Absolute path to the PKCS#12 keystore |
| `BRUSH_STORE_PASSWORD` | Store password |
| `BRUSH_KEY_ALIAS` | Private key alias |
| `BRUSH_KEY_PASSWORD` | Key password |

```sh
./gradlew lintRelease testReleaseUnitTest assembleRelease bundleRelease
```

The APK is written to `app/build/outputs/apk/release`. The AAB is written to `app/build/outputs/bundle/release`.

## GitHub Actions

The Android workflow checks lint, the merged manifest and every level on pushes and pull requests. Tag pushes and manually requested signing runs build and verify signed APK/AAB artifacts. Supply a repository Actions secret named `BRUSH_RELEASE_SIGNING` with compact JSON fields `storeB64` (the base64-encoded PKCS#12 file), `storePassword`, `keyPassword` and `alias`. Keep the private keystore and signing secret outside the repository. From the main branch, choose **Run workflow**, enter `refs/tags/v1.0.0` in `source_ref`, and select **sign** to rebuild the published tag with the current workflow. GitHub release downloads are published separately from the build job.

## Source layout

- `Levels.kt` contains the authored layouts and deterministic solution fixtures.
- `Drawing.kt` contains stroke sampling, ink budgets, validation and segment simplification.
- `Game.kt` handles fixed-step simulation, character movement, collision, switches, hazards and retries.
- `Scene.kt` draws cached artwork, the terrain, the explorer, environmental effects and ink.
- `MainActivity.kt` provides Compose navigation, controls, game timing, HUD and popups.
- `Progress.kt` and `Audio.kt` manage local progress/preferences and offline sound.
- `store/icon-512.png` is the Play Store icon. See [asset provenance](ASSET-LICENSES.md).
