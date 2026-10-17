# Suzumori（鈴守）

在陸版 HyperOS 上守護 Google Play 服務（GMS）不被凍結，讓 FCM 推播準時送達。

不需要 root、Shizuku 或常駐 ADB，以一般 App 的身分運作，可以在關閉開發者選項的情況下使用。

> **目前進度：第 0 階段（可行性診斷版）。**
> 這一版只有一個診斷頁面，用來確認一般 App 身分能不能寫入兩份名單、能不能送出解凍與重連廣播。
> 還沒有自動守護、常駐服務與開機啟動。

## 運作原理

陸版 HyperOS 的凍結機制（Greezer）會在螢幕關閉後很快凍結 GMS，FCM 連線因此中斷。Suzumori 利用系統本身的兩份豁免名單，以及一個解凍廣播來對抗它。

### 白天：`MILLET_NO_RESTRICT_APP`

- `Settings.System` 中以逗號分隔的套件名單。Greezer 在多條凍結路徑上會先查這份名單，名單內的 App 不會被凍結。
- 這份名單由 PowerKeeper 從自己的資料庫產生，每次重建都會整串覆寫，把 GMS 洗掉。重建時機包括開機、安裝或移除 App、使用者修改任何 App 的省電策略等。
- 所以它沒辦法永久生效，只能監聽變化、立刻補回。補回時保留所有既有項目，只在 GMS 不存在時追加。

### 夜間：`com.miui.game.allowlist`

- HyperOS 4 在 23:00 到 07:59、螢幕關閉且裝置 idle 時，會讓上面那份名單失效（原因標記為 `nightDoze`）。
- 遊戲預下載豁免名單（`Settings.System`，以底線分隔）在凍結前會被檢查，名單內的 App 一律拒絕凍結，而且沒有時段限制。
- 系統開機時不會主動讀取這個值，只在值改變時載入。所以每次開機後要在結尾加上或移除一個底線，讓字串改變、成員不變，觸發系統重新載入。
- 安全中心和 Joyose 也會寫這份名單，有時會整串清空。Suzumori 會保留它們的項目，被清掉時補回。

### 解凍與重連

- 加入名單**不會解凍**已經被凍結的 GMS。
- 解凍：送廣播 `com.xiaomi.mipush.REFRESH_WHITE_LIST_ACTION` 給 `android`，帶 `pkgName=com.google.android.gms`、`option=-1`。系統會先解凍 GMS 在主使用者下的 UID，`-1` 不會改動任何 MiPush 名單。
- 重連：等 1 到 2 秒後，送 `GCM_RECONNECT` 與 `MCS_HEARTBEAT`／`GTALK_HEARTBEAT` 心跳給 GMS。
- `sendBroadcast()` 沒有拋出例外只代表「已送出」，不代表對方有收到。是否真的生效，要開 FCM 診斷頁面確認。

### 為什麼 targetSdk 固定為 22

一般 App 寫入非公開的 `Settings.System` key 時，targetSdk 23 以上會被拋出 `IllegalArgumentException`；targetSdk 22 以下只要持有 `WRITE_SETTINGS`，系統只記錄警告並允許寫入。Suzumori 的核心功能完全依賴這個相容行為，所以 targetSdk **不能提高**。

## 安裝

Android 14 起，AOSP 會擋下 targetSdk 過舊的 App（Android 15 起門檻是 24），直接點 APK 安裝可能失敗，錯誤是 `INSTALL_FAILED_DEPRECATED_SDK_VERSION`。

1. 先試著直接在手機上點 APK 安裝。
2. 如果被擋，暫時開啟開發者選項與 USB 偵錯（或無線偵錯），在電腦上執行一次：

   ```
   adb install --bypass-low-target-sdk-block app-debug.apk
   ```

3. 裝好之後就可以關掉開發者選項，之後不再需要 ADB。

更新版本時，簽章必須和已安裝的版本相同。debug 版與 release 版的簽章不同，要互換時必須先解除安裝，**解除安裝前請先做清理**（見下方）。

## 設定步驟（第 0 階段）

1. 第一次開啟時，系統會先顯示權限審查畫面（舊版 App 的標準流程），按「繼續」。
2. 系統會提示「此應用程式專為舊版 Android 打造」，按「確定」即可，這是 targetSdk 22 的正常現象。
3. 確認「寫入權限」顯示「已開啟」。如果沒有，按「前往授權頁」開啟「修改系統設定」。
4. 依序測試各個按鈕，最後用「分享診斷報告」把整份結果傳出來。

## 限制

- 一般 App 讀不到 FCM 連線狀態（Android 10 起讀不到 `/proc/net/tcp`），所以 Suzumori 無法知道 GMS 是否被凍結或連線是否中斷，只能在已知的時機主動解凍和重連。
- 名單被覆寫到 Suzumori 補回之間，仍有短暫的空窗。系統沒有原子操作，和 PowerKeeper、安全中心、Joyose 的競爭無法完全避免。
- 只保護主使用者。系統只看主使用者的設定值，解凍廣播也只對主使用者有效，分身空間不在範圍內。
- Suzumori 本身也可能被凍結或被殺掉。之後的版本會引導把本 App 設為省電「無限制」並開啟自啟動。
- 不能關閉 Greezer 的 GMS 限制器（需要 DUMP 權限），也不能修改其他 App 的自啟動、省電策略等設定。
- 含有底線的套件名稱無法放進遊戲白名單。

## 解除安裝前的清理

**請在解除安裝之前先清理。** 解除安裝後 App 就沒有機會再執行清理，留在遊戲白名單中的項目會一直讓 GMS 免於凍結。

- 在第 0 階段的診斷頁按「清理測試寫入」。
- 清理只移除遊戲白名單中**本 App 加入的項目**（依第一次寫入前記錄的基準判斷），其他程式的項目會保留。
- 沒有剩下任何項目時寫入空字串，不會刪除 key。直接刪除 key 時系統不會清除快取，被移除的項目會繼續受到保護。
- `MILLET_NO_RESTRICT_APP` 不需要清理，PowerKeeper 下次重建名單時會自然移除 GMS。

如果已經解除安裝而沒有清理，可以重新安裝後再清理；但基準紀錄會隨解除安裝消失，這時只能用 ADB 手動修改：

```
adb shell settings get system com.miui.game.allowlist
adb shell settings put system com.miui.game.allowlist "<移除 com.google.android.gms 之後剩下的值>"
```

只剩 GMS 時寫入空字串 `""`，不要用 `settings delete`。

## 建置

需求：JDK 17 以上、Android SDK（compileSdk 37）。

```
./gradlew testDebugUnitTest     # 單元測試
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease       # 需要 keystore.properties 才會簽章
```

release 簽章資訊放在專案根目錄的 `keystore.properties`（已列入 `.gitignore`，不要提交）：

```
storeFile=suzumori-release.jks
storePassword=...
keyAlias=suzumori
keyPassword=...
```
