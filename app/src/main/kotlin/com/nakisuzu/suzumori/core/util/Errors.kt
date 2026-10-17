package com.nakisuzu.suzumori.core.util

/** 例外的類別名稱與訊息，用於紀錄與畫面顯示。 */
fun describe(e: Throwable): String = buildString {
    append(e.javaClass.name)
    e.message?.let { append(": ").append(it) }
}
