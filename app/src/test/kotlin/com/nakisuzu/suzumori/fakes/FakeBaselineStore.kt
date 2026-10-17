package com.nakisuzu.suzumori.fakes

import com.nakisuzu.suzumori.core.baseline.BaselineStore
import com.nakisuzu.suzumori.core.baseline.GameAllowlistBaseline

class FakeBaselineStore(var baseline: GameAllowlistBaseline? = null) : BaselineStore {
    override suspend fun get() = baseline

    override suspend fun recordIfAbsent(baseline: GameAllowlistBaseline): GameAllowlistBaseline =
        this.baseline ?: baseline.also { this.baseline = it }

    override suspend fun clear() {
        baseline = null
    }
}
