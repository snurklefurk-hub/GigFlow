package app.gigflow.driver

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Private-prototype update channel: published, signed APKs in the public GitHub repository.
 *
 * Only specially tagged update releases with a SHA-256 digest are eligible. The OS
 * independently verifies that an APK's signing certificate matches the installed app.
 * Network/file operations MUST run on a worker thread.
 */
object InAppUpdates {
    data class Release(val versionCode: Int, val downloadUrl: String, val sha256: String)

    private const val RELEASES_API =
        "https://api.github.com/repos/snurklefurk-hub/GigFlow/releases?per_page=100"
    private const val ASSET_BASE =
        "https://github.com/snurklefurk-hub/GigFlow/releases/download/"
    private const val ASSET_NAME = "Ride-Offer-Reader.apk"
    private const val MAX_APK_BYTES = 50L * 1024 * 1024
    private val TAG = Regex("^ror-v([1-9][0-9]*)$")
    private val CHECKSUM = Regex("(?im)^SHA256:\\s*([0-9a-f]{64})\\s*$")

    fun installedVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else
            info.versionCode.toLong()
    }

    /** Returns null if no newer *signed-update-channel* release exists. */
    fun findUpdate(context: Context): Release? {
        val conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "RideOfferReader-UpdateChecker")
        }
        val json = try {
            if (conn.responseCode != 200) {
                throw IOException("Update server returned HTTP ${conn.responseCode}")
            }
            JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
        val current = installedVersionCode(context)
        var best: Release? = null
        for (i in 0 until json.length()) {
            val item = json.optJSONObject(i) ?: continue
            if (item.optBoolean("draft") || item.optBoolean("prerelease")) continue
            val tag = item.optString("tag_name")
            val code = TAG.matchEntire(tag)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (code.toLong() <= current || (best != null && code <= best.versionCode)) continue
            val digest = CHECKSUM.find(item.optString("body"))?.groupValues?.get(1) ?: continue
            val assets = item.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                if (asset.optString("name") != ASSET_NAME) continue
                val url = asset.optString("browser_download_url")
                if (url != ASSET_BASE + tag + "/" + ASSET_NAME) continue
                best = Release(code, url, digest.lowercase(Locale.US))
                break
            }
        }
        return best
    }

    /** Download to private cache; reject oversized, changed, or mislabeled APKs. */
    fun download(context: Context, release: Release): File {
        require(release.downloadUrl.startsWith(ASSET_BASE)) { "Untrusted download address" }
        val target = File(context.cacheDir, "ride-offer-update-${release.versionCode}.apk")
        target.delete()
        val conn = (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "RideOfferReader-Updater")
        }
        try {
            if (conn.responseCode != 200 || conn.url.protocol != "https") {
                throw IOException("Secure update download failed (HTTP ${conn.responseCode})")
            }
            if (conn.contentLengthLong > MAX_APK_BYTES) {
                throw IOException("Update exceeds size limit")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val len = input.read(buffer)
                        if (len < 0) break
                        count += len
                        if (count > MAX_APK_BYTES) throw IOException("Update exceeds size limit")
                        digest.update(buffer, 0, len)
                        output.write(buffer, 0, len)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (count == 0L || !actual.equals(release.sha256, ignoreCase = true)) {
                throw IOException("Update failed its integrity check")
            }
            val packageInfo = context.packageManager.getPackageArchiveInfo(target.absolutePath, 0)
                ?: throw IOException("Downloaded update is not a valid APK")
            val code = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else
                packageInfo.versionCode.toLong()
            if (packageInfo.packageName != context.packageName || code != release.versionCode.toLong()) {
                throw IOException("Update package name or version does not match")
            }
            return target
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** Stage the verified APK; Android will ask the user for install confirmation. */
    fun install(context: Context, apk: File, release: Release) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0L, apk.length()).use { stream ->
                    apk.inputStream().use { it.copyTo(stream) }
                    session.fsync(stream)
                }
                val callback = Intent(context, UpdateInstallReceiver::class.java).apply {
                    action = UpdateInstallReceiver.ACTION_INSTALL_RESULT
                }
                val pending = PendingIntent.getBroadcast(
                    context, sessionId, callback,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            try { installer.abandonSession(sessionId) } catch (_: Exception) { }
            throw e
        }
    }
}
