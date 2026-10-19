## 改了什麼 / What changed

<!-- 簡述改動與原因；有相關 issue 請附上，例如 Fixes #12 / Describe the change and why. Link related issues, e.g. Fixes #12 -->

## 如何測試 / How it was tested

<!-- 測試的裝置、系統版本與結果 / Device, system version and results -->

## 確認事項 / Checklist

- [ ] `./gradlew testDebugUnitTest lintRelease assembleDebug` 通過 / passes
- [ ] 已在陸版 HyperOS 實機測試 / Tested on a real device running Chinese HyperOS
- [ ] 介面文字已更新 `values`、`values-zh-rTW`、`values-zh-rCN` 三份字串資源 / UI text updated in all three string resource files
- [ ] targetSdk 維持 22，沒有新增權限或依賴（或已在 issue 討論過）/ targetSdk stays at 22, no new permissions or dependencies (or discussed in an issue)
