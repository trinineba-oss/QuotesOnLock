package com.trinineba.quotesonlock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** A build published on GitHub as the "latest" release. */
data class Release(val build: Long, val notes: String, val apkUrl: String, val apkSize: Long)

/**
 * Updates the app from the repository's GitHub "latest" release. CI numbers every build and writes
 * "Build N" into the release notes; a higher number than ours is an update. Android always asks the
 * user to confirm the install.
 */
object Updater {
    // Plain release downloads, not api.github.com: the API allows only 60 anonymous requests an hour
    // per IP address, which phones sharing a mobile network's address use up quickly.
    private const val DOWNLOAD_BASE = "https://github.com/trinineba-oss/QuotesOnLock/releases/download/latest"
    private const val VERSION_URL = "$DOWNLOAD_BASE/version.json"
    const val APK_DOWNLOAD = "$DOWNLOAD_BASE/quotes-on-lock.apk"
    private const val APK_URL = APK_DOWNLOAD
    const val RELEASES_PAGE = "https://github.com/trinineba-oss/QuotesOnLock/releases/tag/latest"
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 2
    private const val WORK_NAME = "update-check"

    fun currentBuild(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    private fun open(url: String, readTimeout: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            this.readTimeout = readTimeout
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", "QuotesOnLock-Android")
        }

    /** Reads version.json ({build, notes}) published next to the APK. Blocking; call off the main thread. */
    fun fetchLatest(): Release {
        val connection = open(VERSION_URL, 15_000)
        try {
            when (val code = connection.responseCode) {
                200 -> Unit
                404 -> error("GitHub has no version file yet (a new build may be publishing; try again in a few minutes)")
                else -> error("GitHub answered $code")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            return Release(json.getLong("build"), json.optString("notes").trim(), APK_URL, json.optLong("size"))
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads the APK into the app's cache, reporting progress from 0 to 1. Blocking. */
    fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "quotes-on-lock.apk")
        val connection = open(release.apkUrl, 30_000)
        try {
            if (connection.responseCode != 200) error("Download failed (${connection.responseCode})")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return file
    }

    /** Whether Android lets this app open the installer ("Install unknown apps" is allowed). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" switch for this app. */
    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Hands the downloaded APK to Android's installer, which asks the user to confirm. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Turns the weekly background check on or off. */
    fun schedule(context: Context, on: Boolean) {
        val work = WorkManager.getInstance(context.applicationContext)
        if (!on) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Posts "Update available" once per new build. */
    fun notifyIfNew(context: Context, release: Release) {
        val prefs = QuotePrefs(context)
        if (release.build <= currentBuild(context) || release.build <= prefs.notifiedBuild) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Tells you when a new build of the app is on GitHub" }
        )
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quote)
            .setContentTitle("Quotes on Lock update available")
            .setContentText("Build ${release.build} is ready. Tap to install.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            manager.notify(NOTIFICATION_ID, notification)
            prefs.notifiedBuild = release.build
        }
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        Updater.notifyIfNew(applicationContext, Updater.fetchLatest())
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
