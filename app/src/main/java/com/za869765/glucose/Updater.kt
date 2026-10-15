package com.za869765.glucose

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 查 GitHub Releases 最新版（repo 公開，匿名可讀），有新版就提示下載並交給系統安裝。
 * Release tag 格式：v<versionName>+<versionCode>，例 v1.0.1+2。
 */
object Updater {
    private const val API = "https://api.github.com/repos/za869765/glucose-report/releases/latest"
    private const val COOLDOWN_MS = 2L * 60 * 1000
    const val ACTION_INSTALL_STATUS = "com.za869765.glucose.INSTALL_STATUS"

    private val main = Handler(Looper.getMainLooper())
    private var lastCheck = 0L

    private fun currentCode(ctx: Context): Long =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode

    fun check(act: Activity) {
        val now = System.currentTimeMillis()
        if (now - lastCheck < COOLDOWN_MS) return
        lastCheck = now
        Thread {
            val info = runCatching {
                val conn = URL(API).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                if (conn.responseCode != 200) return@runCatching null
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val tag = json.getString("tag_name")
                val code = tag.substringAfterLast('+', "").toLongOrNull() ?: return@runCatching null
                if (code <= currentCode(act)) return@runCatching null
                val assets = json.getJSONArray("assets")
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@runCatching null
                tag.removePrefix("v").substringBefore('+') to apk.getString("browser_download_url")
            }.getOrNull() ?: return@Thread
            main.post {
                if (act.isFinishing) return@post
                AlertDialog.Builder(act)
                    .setTitle("發現新版本")
                    .setMessage("目前版本 ${versionName(act)}，最新版本 ${info.first}。\n要現在更新嗎？")
                    .setPositiveButton("更新") { _, _ -> download(act, info.second) }
                    .setNegativeButton("稍後", null)
                    .show()
            }
        }.start()
    }

    fun versionName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""

    private fun download(act: Activity, url: String) {
        // 第一次需允許「安裝未知應用程式」
        if (!act.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(act, "請允許此 App 安裝更新，返回後會再提示", Toast.LENGTH_LONG).show()
            lastCheck = 0L
            act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}")))
            return
        }
        val progress = AlertDialog.Builder(act).setMessage("下載更新中…").setCancelable(false).show()
        Thread {
            val ok = runCatching {
                val installer = act.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 15000
                    conn.readTimeout = 60000
                    conn.inputStream.use { input ->
                        session.openWrite("update.apk", 0, -1).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    val intent = Intent(act, MainActivity::class.java).setAction(ACTION_INSTALL_STATUS)
                    val pi = PendingIntent.getActivity(
                        act, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(pi.intentSender)
                }
            }.isSuccess
            main.post {
                progress.dismiss()
                if (!ok) Toast.makeText(act, "下載更新失敗，請稍後再試", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    /** 系統安裝流程回呼：需要使用者確認時跳出安裝畫面。 */
    fun onInstallStatus(act: Activity, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) act.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Toast.makeText(
                act, "更新失敗：" + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
