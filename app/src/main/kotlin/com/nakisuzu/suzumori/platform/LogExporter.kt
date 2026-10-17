package com.nakisuzu.suzumori.platform

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.nakisuzu.suzumori.core.util.describe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 把事件紀錄匯出成純文字檔，透過系統分享選單傳出。
 *
 * 2000 筆紀錄可能超過 1 MB，直接放進 Intent 的文字欄位會超過 Binder 的大小限制，所以改用檔案分享。
 */
object LogExporter {
    private const val DIRECTORY = "exports"

    suspend fun write(context: Context, text: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "suzumori-events.txt")
        file.writeText(text)
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** 成功時回傳 null，失敗時回傳例外描述。 */
    fun share(context: Context, uri: Uri): String? {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Suzumori 事件紀錄")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("suzumori-events.txt", uri)
        return try {
            context.startActivity(Intent.createChooser(send, "匯出事件紀錄"))
            null
        } catch (e: Exception) {
            describe(e)
        }
    }
}
