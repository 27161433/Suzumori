package com.nakisuzu.suzumori.platform

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.SuzumoriApp
import com.nakisuzu.suzumori.core.guard.Trigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 快速設定磚「解凍並重連」：先確保名單正常，再解凍並重連 GMS。 */
class UnfreezeTileService : TileService() {
    override fun onStartListening() {
        setTileState(active = false)
    }

    override fun onClick() {
        val container = (application as SuzumoriApp).container
        setTileState(active = true)
        container.appScope.launch {
            container.controller.ensureAndUnfreeze(Trigger.TILE)
            withContext(Dispatchers.Main) { setTileState(active = false) }
        }
    }

    private fun setTileState(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (active) getString(R.string.tile_sending) else null
        tile.updateTile()
    }
}
