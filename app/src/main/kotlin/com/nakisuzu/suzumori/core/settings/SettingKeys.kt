package com.nakisuzu.suzumori.core.settings

/** 本 App 會讀寫的 `Settings.System` key。 */
object SettingKeys {
    /** Greezer 的不凍結名單，逗號分隔，由 PowerKeeper 擁有並整串覆寫。 */
    const val MILLET_NO_RESTRICT_APP = "MILLET_NO_RESTRICT_APP"

    /** 遊戲預下載豁免名單，底線分隔，`GreezeManagerService.freezeUids()` 會拒絕凍結名單內的 App。 */
    const val GAME_ALLOWLIST = "com.miui.game.allowlist"
}
