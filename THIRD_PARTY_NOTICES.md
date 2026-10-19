# Third-party notices

Suzumori is licensed under the [GNU General Public License v3.0](LICENSE). It includes or depends on the following third-party work.

本專案包含或依賴以下第三方作品，各自依其授權條款使用。

## Source code included in this repository

### Liquid glass navigation bar

- Files: `app/src/main/kotlin/com/nakisuzu/suzumori/ui/components/glass/`
- Copyright 2026, compose-miuix-ui contributors
- Copied from the [miuix](https://github.com/compose-miuix-ui/miuix) v0.9.4 example app (`example/shared/src/commonMain/kotlin/component/liquid/`), which is adapted from [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass).
- License: Apache License 2.0, full text in [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt).
- Changes: uses Suzumori's colors, removes badges and the iOS-only bottom padding. Each file keeps its original copyright and license header and notes the changes.

## Libraries bundled in the app

| Library | License |
|---|---|
| [AndroidX](https://developer.android.com/jetpack/androidx) (Activity, Compose, Core, DataStore, Lifecycle, WorkManager) | Apache License 2.0 |
| [Kotlin standard library](https://github.com/JetBrains/kotlin) and [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Apache License 2.0 |
| [miuix](https://github.com/compose-miuix-ui/miuix) (ui, preference, blur, icons) | Apache License 2.0 |
| [MaterialKolor](https://github.com/jordond/materialkolor) (dependency of miuix) | MIT License |

The Apache License 2.0 text is in [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt). The MIT License text is distributed with MaterialKolor.
