# 參與貢獻 / Contributing

[繁體中文](#繁體中文) ・ [English](#english)

## 繁體中文

感謝你願意幫忙！Suzumori 是個人維護的小專案，回覆可能需要幾天。

### 回報問題

1. 先搜尋[現有的 issue](https://github.com/27161433/Suzumori/issues)，避免重複。
2. 使用「問題回報」範本，並附上：
   - 手機型號與 HyperOS 版本（「關於」頁的系統版本）。
   - Suzumori 版本（「關於」頁的版本）。
   - 事件紀錄頁右上角匯出的紀錄。紀錄包含名單內容（例如設為「無限制」的 App 套件名稱）與時間，上傳前可以自己檢查一遍，刪掉不想公開的部分。
   - 如果方便，附上 `adb logcat -s Suzumori` 與 `adb shell dumpsys greezer` 的輸出。
3. 安全性問題請不要開公開 issue，依 [SECURITY.md](SECURITY.md) 私下回報。

### 提交程式碼

1. 較大的改動請先開 issue 討論方向，避免白做。
2. Fork 之後從 `main` 開新分支。
3. 送出 pull request 前請確認以下指令都通過：

   ```
   ./gradlew testDebugUnitTest lintRelease assembleDebug
   ```

4. 在實機（陸版 HyperOS）上測試過，並在 PR 說明測試的裝置與結果。

### 專案慣例

- **targetSdk 必須維持 22**，原因見 README 的「為什麼 targetSdk 固定為 22」。
- 只使用 Google（AndroidX、Jetpack）與 JetBrains（Kotlin、kotlinx）官方的函式庫；介面另外使用 miuix。新增其他依賴前請先討論。
- 網路只用於向 GitHub 檢查更新（`platform/Updates.kt`），不要新增其他連線，也不加入任何分析、統計或錯誤回報 SDK。
- 程式碼註解、commit 訊息使用繁體中文；用英文提交也沒問題。
- 介面文字放在字串資源，`values`（英文）、`values-zh-rTW`、`values-zh-rCN` 三份都要更新，lint 會檢查是否缺漏。
- `app/src/main/kotlin/.../core/` 不依賴 Android，邏輯放在這裡並寫單元測試。
- 與系統行為有關的判斷（凍結、名單改寫、廣播），請盡量附上實機的 logcat 或 dumpsys 作為依據。

### 授權

送出的貢獻會以本專案的 [GPL-3.0](LICENSE) 授權釋出。

## English

Thanks for helping! Suzumori is a small project maintained by one person, so replies may take a few days.

### Reporting a problem

1. Search the [existing issues](https://github.com/27161433/Suzumori/issues) first to avoid duplicates.
2. Use the bug report template and include:
   - Your phone model and HyperOS version (System version on the About page).
   - The Suzumori version (shown on the About page).
   - The log exported from the top right of the event log page. It contains list contents (such as the package names of apps set to "No restrictions") and times; review it before uploading and remove anything you don't want to share.
   - If you can, the output of `adb logcat -s Suzumori` and `adb shell dumpsys greezer`.
3. Don't open public issues for security problems; report them privately as described in [SECURITY.md](SECURITY.md).

### Submitting code

1. For larger changes, open an issue first to agree on the direction.
2. Fork the repository and branch off `main`.
3. Before opening a pull request, make sure this passes:

   ```
   ./gradlew testDebugUnitTest lintRelease assembleDebug
   ```

4. Test on a real device running Chinese HyperOS, and describe the device and results in the PR.

### Project conventions

- **targetSdk must stay at 22**; see "Why targetSdk is fixed at 22" in the README.
- Only official Google (AndroidX, Jetpack) and JetBrains (Kotlin, kotlinx) libraries, plus miuix for the UI. Please discuss before adding any other dependency.
- The network is only used to check GitHub for updates (`platform/Updates.kt`). Don't add other connections, and no analytics, tracking or crash-reporting SDKs.
- Code comments and commit messages are in Traditional Chinese, but English is fine for contributions.
- UI text lives in string resources. Update all three of `values` (English), `values-zh-rTW` and `values-zh-rCN`; lint checks for missing translations.
- `app/src/main/kotlin/.../core/` doesn't depend on Android. Put logic there and cover it with unit tests.
- For anything that depends on system behavior (freezing, list rewrites, broadcasts), please back it up with logcat or dumpsys output from a real device.

### License

Contributions are released under the project's [GPL-3.0](LICENSE) license.
