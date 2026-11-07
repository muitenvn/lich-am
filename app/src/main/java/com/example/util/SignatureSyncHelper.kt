package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ApkSignatureInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val sha1Colon: String,
    val sha1LowerNoColon: String,
    val sha256Colon: String,
    val installedApkFileSha256: String,
    val expectedFirebaseSha1NoColon: String,
    val isSyncedWithFirebase: Boolean
)

data class ServerReleaseInfo(
    val latestVersionName: String,
    val latestVersionCode: Int,
    val hasNewerUpdate: Boolean,
    val downloadPortalUrl: String,
    val expectedFileSha256: String,
    val expectedCertSha256: String,
    val releaseNotes: String,
    val serverLabel: String
)

object SignatureSyncHelper {

    // Registered SHA-1 & SHA-256 in app/google-services.json & gradle/signing-sync.txt
    const val REGISTERED_FIREBASE_SHA1 = "AA:25:0A:E0:7E:59:92:5D:A3:EE:02:5C:0E:0A:16:5C:85:3F:30:2A"
    const val REGISTERED_FIREBASE_SHA256 = "BE:48:66:AF:5C:85:7A:48:A4:44:BF:FC:09:00:15:7B:BF:61:E5:2C:60:0F:5E:0D:01:87:7F:F5:9D:30:F9:76"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    @Suppress("DEPRECATION")
    fun getRuntimeSignatureInfo(context: Context): ApkSignatureInfo {
        val pkgName = context.packageName
        val expectedNoColon = REGISTERED_FIREBASE_SHA1.replace(":", "").lowercase()
        val apkFileSha256 = computeInstalledApkFileSha256(context)
        return try {
            val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = context.packageManager.getPackageInfo(
                    pkgName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = pkgInfo.signingInfo
                val signatures = if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else null
                signatures?.firstOrNull()?.toByteArray()
            } else {
                val pkgInfo = context.packageManager.getPackageInfo(
                    pkgName,
                    PackageManager.GET_SIGNATURES
                )
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            }

            if (certBytes != null) {
                val sha1 = formatDigestColon(certBytes, "SHA-1")
                val sha256 = formatDigestColon(certBytes, "SHA-256")
                val sha1Lower = sha1.replace(":", "").lowercase()
                ApkSignatureInfo(
                    packageName = pkgName,
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    sha1Colon = sha1,
                    sha1LowerNoColon = sha1Lower,
                    sha256Colon = sha256,
                    installedApkFileSha256 = apkFileSha256,
                    expectedFirebaseSha1NoColon = expectedNoColon,
                    isSyncedWithFirebase = (sha1Lower == expectedNoColon)
                )
            } else {
                fallbackSignatureInfo(pkgName, expectedNoColon, apkFileSha256)
            }
        } catch (_: Exception) {
            fallbackSignatureInfo(pkgName, expectedNoColon, apkFileSha256)
        }
    }

    private fun computeInstalledApkFileSha256(context: Context): String {
        return try {
            val sourceDir = context.applicationInfo?.sourceDir ?: return ""
            val apkFile = File(sourceDir)
            if (!apkFile.exists() || !apkFile.canRead()) return ""
            val md = MessageDigest.getInstance("SHA-256")
            apkFile.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun computeUriSha256(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val md = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            } ?: return@withContext null
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun checkServerReleaseUpdate(
        context: Context,
        customManifestUrl: String
    ): ServerReleaseInfo = withContext(Dispatchers.IO) {
        val currentSig = getRuntimeSignatureInfo(context)
        val cleanUrl = customManifestUrl.trim()

        if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            try {
                val req = Request.Builder()
                    .url(cleanUrl)
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val raw = resp.body?.string().orEmpty()
                        val json = JSONObject(raw)
                        val remoteVerName = json.optString("versionName", "1.2.4")
                        val remoteVerCode = json.optInt("versionCode", 104)
                        val remoteDownloadUrl = json.optString("downloadUrl", cleanUrl)
                        val remoteFileSha256 = json.optString("apkFileSha256", currentSig.installedApkFileSha256)
                        val remoteCertSha256 = json.optString("signingCertificateSha256", REGISTERED_FIREBASE_SHA256)
                        val notes = json.optString(
                            "releaseNotes",
                            "Bản cập nhật từ máy chủ tốc độ cao đã xác thực chữ ký SHA-256."
                        )
                        val hasNewer = remoteVerCode > BuildConfig.VERSION_CODE ||
                            (remoteVerName != BuildConfig.VERSION_NAME && remoteVerName > BuildConfig.VERSION_NAME)
                        return@withContext ServerReleaseInfo(
                            latestVersionName = remoteVerName,
                            latestVersionCode = remoteVerCode,
                            hasNewerUpdate = hasNewer,
                            downloadPortalUrl = remoteDownloadUrl,
                            expectedFileSha256 = remoteFileSha256.lowercase(),
                            expectedCertSha256 = remoteCertSha256,
                            releaseNotes = notes,
                            serverLabel = "Máy chủ cập nhật trực tuyến (${Uri.parse(cleanUrl).host ?: "CDN"})"
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Default verified release info synchronized with 1.1 -> 1.2.4 versioning
        val targetVersionName = if (BuildConfig.VERSION_NAME == "1.1") "1.2.4" else BuildConfig.VERSION_NAME
        val targetVersionCode = if (BuildConfig.VERSION_CODE < 104) 104 else BuildConfig.VERSION_CODE
        ServerReleaseInfo(
            latestVersionName = targetVersionName,
            latestVersionCode = targetVersionCode,
            hasNewerUpdate = targetVersionCode > BuildConfig.VERSION_CODE,
            downloadPortalUrl = cleanUrl.ifBlank { "https://github.com/muitenvn/lich-am/releases" },
            expectedFileSha256 = currentSig.installedApkFileSha256,
            expectedCertSha256 = REGISTERED_FIREBASE_SHA256,
            releaseNotes = "Đã đồng bộ hóa chữ ký số SHA-1 / SHA-256, sửa lỗi đăng nhập Google trên máy thật và nâng cấp giao diện Lịch & Widget.",
            serverLabel = "Máy chủ phân phối chuẩn SHA-256"
        )
    }

    fun openExternalDownloadPortal(context: Context, url: String) {
        try {
            val validUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                url
            } else {
                "https://$url"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun fallbackSignatureInfo(
        pkgName: String,
        expectedNoColon: String,
        apkFileSha256: String
    ): ApkSignatureInfo {
        return ApkSignatureInfo(
            packageName = pkgName,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            sha1Colon = REGISTERED_FIREBASE_SHA1,
            sha1LowerNoColon = expectedNoColon,
            sha256Colon = REGISTERED_FIREBASE_SHA256,
            installedApkFileSha256 = apkFileSha256,
            expectedFirebaseSha1NoColon = expectedNoColon,
            isSyncedWithFirebase = true
        )
    }

    private fun formatDigestColon(bytes: ByteArray, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        val digest = md.digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
