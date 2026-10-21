package com.nakisuzu.suzumori.platform

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HintStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(file: File, job: Job) =
        HintStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))

    @Test
    fun `FCM App 檢查的說明預設顯示，關閉後不再顯示，重新開啟 App 也一樣`() = runTest {
        val file = File(folder.root, "hints.preferences_pb")

        val first = Job()
        val hints = store(file, first)
        assertTrue(hints.showFcmAppsIntro.first())
        hints.dismissFcmAppsIntro()
        assertFalse(hints.showFcmAppsIntro.first())
        first.cancelAndJoin()

        // 同一個檔案重新建立，等同 App 重新啟動。
        val second = Job()
        assertFalse(store(file, second).showFcmAppsIntro.first())
        second.cancelAndJoin()
    }
}
