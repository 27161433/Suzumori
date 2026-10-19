package com.nakisuzu.suzumori.platform

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.core.log.EventEntry
import com.nakisuzu.suzumori.core.log.EventLogText
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

    /** 匯出用的純文字，以目前的 App 語言組成。[entries] 舊到新，輸出也是舊到新，方便從頭閱讀一整晚的變化。 */
    fun format(context: Context, entries: List<EventEntry>): String = buildString {
        appendLine(context.getString(R.string.export_header, EventLogText.formatTime(System.currentTimeMillis())))
        appendLine(context.resources.getQuantityString(R.plurals.export_count, entries.size, entries.size))
        appendLine()
        for (entry in entries) {
            val trigger = Labels.trigger(context, entry.trigger)
            appendLine("${EventLogText.formatTime(entry.timeMillis)} [$trigger]${entry.key?.let { " $it" }.orEmpty()}")
            appendLine("  " + context.getString(R.string.export_action, LogTexts.render(context, entry.action)))
            appendLine("  " + context.getString(R.string.export_result, LogTexts.render(context, entry.result)))
            entry.before?.let { appendLine("  " + context.getString(R.string.export_before, LogTexts.value(context, it))) }
            entry.after?.let { appendLine("  " + context.getString(R.string.export_after, LogTexts.value(context, it))) }
        }
    }

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
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_subject))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("suzumori-events.txt", uri)
        return try {
            context.startActivity(Intent.createChooser(send, context.getString(R.string.export_chooser)))
            null
        } catch (e: Exception) {
            describe(e)
        }
    }
}
