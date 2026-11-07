# 更新紀錄 / Changelog

格式參考 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/)，版本號遵循 [語意化版本](https://semver.org/lang/zh-TW/)。

This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and [Semantic Versioning](https://semver.org/).

## [Unreleased]

### 新增

- FCM 斷線後自動補送重連。實測 HyperOS 4 的資源預算（SSRU）用完時，會把 GMS 的所有鬧鐘延後：FCM 因網路變化斷線後 GMS 自己排的重連鬧鐘不會響，GMS 的心跳鬧鐘也不響，所以連線悄悄死掉時它永遠不會發現，通知就一直收不到，直到手動按「解凍並重連」。現在 Suzumori 會自己補送解凍與重連：醒著時每 60 秒一次（GMS 把心跳節流成每分鐘最多一次，更密沒有用）、網路變化後 25 秒一次（用鬧鐘延遲，手機在這期間睡著也會準時），以及每 15 分鐘的定期檢查。GMS 已連線時只會回一次心跳，不會斷線；連線已死時會立刻重連並補收積壓的推播。例行補送不寫入事件紀錄，只更新首頁「最近一次解凍與重連」的時間。
- 設定新增「休眠增強守護」：關閉（預設）、1 分鐘、5 分鐘、10 分鐘。開啟後在螢幕關閉期間每隔這麼久喚醒手機發送解凍與重連，FCM 斷線後最多等這麼久就會接回來，會稍微增加待機耗電。關閉時只在手機自己醒著時發送，最晚等到 15 分鐘的定期檢查。
- 因此新增 `ACCESS_NETWORK_STATE` 權限（一般權限，安裝時自動授予），只用來得知網路變化。

### Added

- Automatic reconnect after FCM drops. On HyperOS 4, once the system's resource budget (SSRU) runs out it defers every GMS alarm: after a network change drops the FCM connection, the reconnect alarm GMS schedules never fires, and because its heartbeat alarm is blocked too, a connection that dies silently is never noticed. Notifications stop until "Unfreeze & reconnect" is tapped. Suzumori now sends unfreeze and reconnect itself: every 60 seconds while the phone is awake (GMS throttles heartbeats to one per minute, so anything faster is wasted), 25 seconds after a network change (delayed with an alarm, so it fires on time even if the phone falls asleep in between), and on every 15-minute periodic check. While already connected GMS just answers one heartbeat and stays connected; when the connection is dead it reconnects at once and the queued pushes arrive. These routine sends are not written to the event log; only the "Last unfreeze" time on the home page is updated.
- New setting "Sleep guard boost": Off (default), 1, 5 or 10 minutes. When on, Suzumori wakes the phone at that interval while the screen is off and sends unfreeze and reconnect, so a dropped FCM connection comes back within that time; this slightly increases standby battery use. When off, it only sends while the phone happens to be awake, and at the latest on the 15-minute periodic check.
- This adds the `ACCESS_NETWORK_STATE` permission (a normal permission, granted at install), used only to notice network changes.

## [1.1.1] - 2026-11-01

### 修正

- 常駐通知選「固定位置：置底」時，安裝滿 14 天後就不再置底、內容也不再更新。Android 會直接丟棄時間早於 14 天前的通知，現在改用不會超過這個期限的時間排序。
- 常駐通知被滑掉後，要等下次開啟 App 才會出現；現在滑掉後會立刻重新顯示。不需要定時檢查，不增加耗電。

### Fixed

- With the persistent notification set to "Fixed position: bottom", it stopped staying at the bottom and stopped updating 14 days after install. Android drops notifications whose time is more than 14 days old; the sort time now stays within that limit.
- Swiping the persistent notification away hid it until the app was opened again. It now comes back right away, with no polling and no extra battery use.

## [1.1.0] - 2026-10-21

### 新增

- 檢查更新：開啟 App 時與每天一次向 GitHub 查詢新版本。有新版本時首頁顯示提示，背景檢查到時發出通知，點開後顯示更新內容與下載連結。設定中可以關閉自動檢查。
- 因此新增 `INTERNET` 權限，只用來連線到 `api.github.com`，不傳送任何個人資料。
- FCM App 檢查：新的「App 檢查」分頁，列出使用 FCM 的 App 與它們的自啟動、省電策略，可以搜尋名稱或套件名稱，並依名稱、自啟動或省電策略排序；點一下開啟該 App 的設定頁。要收通知的 App 需要同時開啟自啟動、省電策略設為無限制，頁面上方有說明（可以關閉）。只在切到這一頁時讀取，不修改任何設定。

### 變更

- 關閉常駐通知後仍會在背景監聽名單變化與螢幕開關；常駐通知只決定是前景服務還是一般服務。
- FCM 診斷頁找不到新版頁面時，改開舊版的 GTalkServiceDiagnostics；兩個都沒有時顯示說明。

### Added

- Update check: asks GitHub for a new version when the app opens and once a day. The home page shows a card when an update is out, and a background check posts a notification that opens the release notes and download link. Automatic checks can be turned off in Settings.
- This adds the `INTERNET` permission, used only to reach `api.github.com`. No personal data is sent.
- FCM apps check: a new Apps tab that lists the apps that use FCM with their autostart and battery saver settings. Search by app or package name, sort by name, autostart or battery saver, and open an app's settings page in one tap. Apps you want notifications from need both autostart and "No restrictions"; a dismissible note at the top says so. It reads only when you switch to the tab and never changes anything.

### Changed

- With the persistent notification off, Suzumori still watches list changes and screen on/off in the background. The setting now only decides whether the service runs in the foreground.
- If the FCM diagnostics page isn't found, the older GTalkServiceDiagnostics page is tried; if neither exists, an explanation is shown.

## [1.0.1] - 2026-10-19

### 修正

- 切換語言時整個畫面不再閃一下（包括打開 App 後的第一次切換）。
- 切換語言後，事件紀錄的日期、首頁的「今天／昨天」與關於頁的系統版本格式會立即換成新的語言。

### Fixed

- Switching the language no longer makes the whole screen flash, including the first switch after opening the app.
- After switching the language, event log dates, "Today/Yesterday" on the home page and the system version format on the About page update right away.

## [1.0.0] - 2026-10-19

第一個公開版本。First public release.

### 功能

- 自動守護：監聽 `MILLET_NO_RESTRICT_APP` 與遊戲白名單，GMS 被移除時立即補回；另外在螢幕開關、開機（含解鎖前）、App 更新時檢查，並每 15 分鐘保底檢查一次。
- 夜間守護：把 GMS（以及本 App）加入遊戲白名單，避免 HyperOS 4 夜間凍結。
- 解凍並重連：App 內與快速設定磚都可以手動執行。
- 事件紀錄：最近 2000 筆名單改寫、修補與廣播，可匯出成純文字。
- 自我監測：記錄本 App 是否曾被暫停、程序被結束的原因（小米的清理原因也會顯示成文字），以及被結束後是否自動重新啟動。
- 首頁提示寫入權限、省電策略、自啟動沒設定好的情況，點一下直接開啟對應的系統頁面。
- 介面支援繁體中文、简体中文、English，以及淺色、深色主題。
- 常駐通知可選系統預設、固定位置（置頂或置底）或可收納。
- 解除安裝前清理：只移除本 App 加入遊戲白名單的項目。

### Features

- Auto guard: watches `MILLET_NO_RESTRICT_APP` and the game allowlist and puts GMS back as soon as it's removed. Also checks on screen on/off, boot (including before unlock) and app updates, with a fallback check every 15 minutes.
- Night guard: adds GMS (and Suzumori) to the game allowlist so HyperOS 4 doesn't freeze it at night.
- Unfreeze & reconnect, from the app or a quick settings tile.
- Event log: the last 2,000 list rewrites, patches and broadcasts, exportable as plain text.
- Self-monitoring: records pauses, why the process was killed (including Xiaomi's cleanup reasons), and whether it restarted afterwards.
- The home page points out missing write permission, battery saver or autostart settings and opens the right system page in one tap.
- 繁體中文, 简体中文 and English, with light and dark themes.
- Persistent notification styles: default, pinned (top or bottom), or foldable.
- Pre-uninstall cleanup that removes only the game allowlist entries Suzumori added.

[1.1.1]: https://github.com/27161433/Suzumori/releases/tag/v1.1.1
[1.1.0]: https://github.com/27161433/Suzumori/releases/tag/v1.1.0
[1.0.1]: https://github.com/27161433/Suzumori/releases/tag/v1.0.1
[1.0.0]: https://github.com/27161433/Suzumori/releases/tag/v1.0.0
