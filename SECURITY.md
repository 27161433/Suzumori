# 安全性政策 / Security policy

## 支援的版本 / Supported versions

只有最新版本會收到安全性修正。Only the latest release receives security fixes.

## 回報漏洞 / Reporting a vulnerability

請**不要**為安全性問題開公開的 issue。請使用 GitHub 的私下回報功能：

1. 到本專案的 [Security 分頁](https://github.com/27161433/Suzumori/security)。
2. 點「Report a vulnerability」，描述問題、影響範圍與重現步驟。

收到後會盡快回覆，並在修正發布後公開說明。

Please **don't** open a public issue for security problems. Use GitHub's private vulnerability reporting instead:

1. Go to the project's [Security tab](https://github.com/27161433/Suzumori/security).
2. Click "Report a vulnerability" and describe the problem, its impact and how to reproduce it.

You'll get a reply as soon as possible, and the issue will be disclosed after a fix is released.

## 範圍 / Scope

Suzumori 會寫入兩個系統設定值（`MILLET_NO_RESTRICT_APP`、`com.miui.game.allowlist`）並送出解凍與重連廣播。以下情況屬於安全性問題：

- 其他 App 能透過 Suzumori 寫入任意系統設定，或以 Suzumori 的身分送出廣播。
- Suzumori 洩漏事件紀錄或其他資料給其他 App。
- Suzumori 移除或破壞其他程式加入名單的項目。

Suzumori writes two system settings (`MILLET_NO_RESTRICT_APP` and `com.miui.game.allowlist`) and sends unfreeze and reconnect broadcasts. These count as security problems:

- Another app can use Suzumori to write arbitrary system settings or send broadcasts as Suzumori.
- Suzumori leaks the event log or other data to other apps.
- Suzumori removes or corrupts entries that other apps added to the lists.
