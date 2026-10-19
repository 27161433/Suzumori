package com.nakisuzu.suzumori.platform

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nakisuzu.suzumori.SuzumoriApp
import com.nakisuzu.suzumori.core.update.AppVersion
import com.nakisuzu.suzumori.core.update.ReleaseInfo
import com.nakisuzu.suzumori.core.util.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.TimeUnit

/** 更新檢查的狀態，給畫面顯示。 */
data class UpdateState(
    /** 目前安裝的版本。 */
    val current: AppVersion? = null,
    /** 最近一次查到的 Release，不一定比目前的版本新。 */
    val latest: ReleaseInfo? = null,
    val checking: Boolean = false,
    /** 最近一次檢查失敗的原因；成功後清除。 */
    val error: String? = null,
    val lastCheckedMillis: Long? = null,
    val autoCheck: Boolean = true,
) {
    /** GitHub 上有比目前更新的版本。 */
    val available: Boolean
        get() = latest != null && current != null && latest.version > current
}

/**
 * 向 GitHub 查詢最新的 Release。
 *
 * 這是 App 唯一的網路連線：只對 `api.github.com` 發出一個不帶任何個人資料的 GET 請求。
 * 開啟 App 時檢查一次，背景每天檢查一次（[UpdateWorker]），有新版本時發通知；
 * 使用者可以在設定中關閉自動檢查，關閉後只在手動按「重新檢查」時連線。
 */
class Updates(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val checking = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val current = AppVersion.parse(runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull())

    val autoCheck = dataStore.data.map { it[AUTO_CHECK] ?: true }

    val state: StateFlow<UpdateState> = combine(dataStore.data, checking, error) { prefs, checking, error ->
        UpdateState(
            current = current,
            latest = prefs.release(),
            checking = checking,
            error = error,
            lastCheckedMillis = prefs[LAST_CHECKED],
            autoCheck = prefs[AUTO_CHECK] ?: true,
        )
    }.stateIn(scope, SharingStarted.Eagerly, UpdateState(current = current))

    /** 開啟 App 時：依設定排程每天的檢查，並在自動檢查開啟時馬上檢查一次。 */
    suspend fun onAppOpen() {
        val enabled = autoCheck.first()
        schedule(enabled)
        if (enabled) {
            // 使用者正在 App 裡，首頁會顯示有新版本，不需要再發通知。
            check()?.let { if (isNewer(it)) markNotified(it) else UpdateNotifications.cancel(context) }
        }
    }

    suspend fun setAutoCheck(enabled: Boolean) {
        dataStore.edit { it[AUTO_CHECK] = enabled }
        schedule(enabled)
    }

    /** 查詢最新的 Release 並保存。失敗時記下原因並回傳 null。同一時間只會有一個查詢。 */
    suspend fun check(): ReleaseInfo? = mutex.withLock {
        checking.value = true
        try {
            val release = fetchLatest()
            dataStore.edit { it.putRelease(release) }
            error.value = null
            Diag.i("update check: latest=${release.version} current=$current")
            release
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Diag.i("update check failed: ${describe(e)}")
            error.value = describe(e)
            null
        } finally {
            checking.value = false
        }
    }

    /** 背景檢查：有新版本而且還沒通知過這個版本時發通知。回傳 false 表示這次檢查失敗。 */
    suspend fun checkInBackground(): Boolean {
        if (!autoCheck.first()) return true
        val release = check() ?: return false
        if (!isNewer(release)) {
            UpdateNotifications.cancel(context)
            return true
        }
        if (dataStore.data.first()[NOTIFIED_TAG] != release.tag) {
            UpdateNotifications.show(context, release)
            markNotified(release)
        }
        return true
    }

    private fun isNewer(release: ReleaseInfo) = current != null && release.version > current

    private suspend fun markNotified(release: ReleaseInfo) {
        dataStore.edit { it[NOTIFIED_TAG] = release.tag }
    }

    private fun schedule(enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                    // 沒有網路時隔一段時間再試，不必等到隔天。
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                    .build(),
            )
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
            UpdateNotifications.cancel(context)
        }
    }

    private suspend fun fetchLatest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Suzumori/$current")
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            parseRelease(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo {
        val tag = json.getString("tag_name")
        val version = AppVersion.parse(tag) ?: throw IOException("Unrecognized version tag: $tag")
        val assets = json.optJSONArray("assets")
        val apk = assets?.let { array ->
            (0 until array.length()).map { array.getJSONObject(it) }.firstOrNull { it.text("name")?.endsWith(".apk") == true }
        }
        return ReleaseInfo(
            version = version,
            tag = tag,
            notes = json.text("body").orEmpty().replace("\r\n", "\n"),
            pageUrl = trusted(json.text("html_url")) ?: "${SystemPages.SOURCE_URL}/releases/latest",
            apkUrl = trusted(apk?.text("browser_download_url")),
            apkSha256 = apk?.text("digest")?.removePrefix("sha256:"),
            publishedAtMillis = json.text("published_at")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        )
    }

    /** 只接受本專案在 GitHub 上的網址，其他網址一律不開，避免更新頁把人帶到別的地方下載。 */
    private fun trusted(url: String?): String? = url?.takeIf { it.startsWith("${SystemPages.SOURCE_URL}/") }

    /** 欄位的文字；不存在、是 JSON null 或空字串時回傳 null（`optString` 遇到 null 會回傳字串 "null"）。 */
    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }

    private fun Preferences.release(): ReleaseInfo? {
        val tag = this[TAG] ?: return null
        return ReleaseInfo(
            version = AppVersion.parse(tag) ?: return null,
            tag = tag,
            notes = this[NOTES].orEmpty(),
            pageUrl = this[PAGE_URL] ?: return null,
            apkUrl = this[APK_URL],
            apkSha256 = this[APK_SHA256],
            publishedAtMillis = this[PUBLISHED_AT],
        )
    }

    private fun MutablePreferences.putRelease(release: ReleaseInfo) {
        this[TAG] = release.tag
        this[NOTES] = release.notes
        this[PAGE_URL] = release.pageUrl
        release.apkUrl?.let { this[APK_URL] = it } ?: remove(APK_URL)
        release.apkSha256?.let { this[APK_SHA256] = it } ?: remove(APK_SHA256)
        release.publishedAtMillis?.let { this[PUBLISHED_AT] = it } ?: remove(PUBLISHED_AT)
        this[LAST_CHECKED] = System.currentTimeMillis()
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/27161433/Suzumori/releases/latest"
        const val TIMEOUT_MILLIS = 15_000
        const val WORK_NAME = "update-check"

        val AUTO_CHECK = booleanPreferencesKey("auto_check")
        val LAST_CHECKED = longPreferencesKey("last_checked_at")
        val NOTIFIED_TAG = stringPreferencesKey("notified_tag")
        val TAG = stringPreferencesKey("latest_tag")
        val NOTES = stringPreferencesKey("latest_notes")
        val PAGE_URL = stringPreferencesKey("latest_page_url")
        val APK_URL = stringPreferencesKey("latest_apk_url")
        val APK_SHA256 = stringPreferencesKey("latest_apk_sha256")
        val PUBLISHED_AT = longPreferencesKey("latest_published_at")
    }
}

/** 每天一次的背景更新檢查。沒有網路等失敗時依退避間隔重試。 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Diag.i("UpdateWorker doWork")
        val updates = (applicationContext as SuzumoriApp).container.updates
        return if (updates.checkInBackground()) Result.success() else Result.retry()
    }
}
