package com.za869765.glucose

import android.content.Context

/** 試片資訊，換新包時在設定修改，存在 SharedPreferences。 */
data class StripInfo(
    val brand: String,
    val lot: String,
    val expiry: String,
    val license: String,
) {
    fun lines(): List<Pair<String, String>> = listOf(
        "試片廠牌" to brand,
        "製造批號" to lot,
        "保存期限" to expiry,
        "許可證號" to license,
    ).filter { it.second.isNotBlank() }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("brand", brand)
            .putString("lot", lot)
            .putString("expiry", expiry)
            .putString("license", license)
            .apply()
    }

    companion object {
        private const val PREFS = "strip"

        fun load(ctx: Context): StripInfo {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return StripInfo(
                brand = p.getString("brand", null) ?: "厚美德 金輕鬆血糖檢測試片",
                lot = p.getString("lot", null) ?: "C4H68031F",
                expiry = p.getString("expiry", null) ?: "117/08/02",
                license = p.getString("license", null) ?: "衛部醫器製字第006265號",
            )
        }
    }
}
