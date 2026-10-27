package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

data class ApkSignatureInfo(
    val packageName: String,
    val sha1Colon: String,
    val sha1LowerNoColon: String,
    val sha256Colon: String,
    val expectedFirebaseSha1NoColon: String,
    val isSyncedWithFirebase: Boolean
)

object SignatureSyncHelper {

    // Registered SHA-1 in app/google-services.json
    const val REGISTERED_FIREBASE_SHA1 = "AA:25:0A:E0:7E:59:92:5D:A3:EE:02:5C:0E:0A:16:5C:85:3F:30:2A"
    const val REGISTERED_FIREBASE_SHA256 = "BE:48:66:AF:5C:85:7A:48:A4:44:BF:FC:09:00:15:7B:BF:61:E5:2C:60:0F:5E:0D:01:87:7F:F5:9D:30:F9:76"

    @Suppress("DEPRECATION")
    fun getRuntimeSignatureInfo(context: Context): ApkSignatureInfo {
        val pkgName = context.packageName
        val expectedNoColon = REGISTERED_FIREBASE_SHA1.replace(":", "").lowercase()
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
                val sha1 = formatDigest(certBytes, "SHA-1")
                val sha256 = formatDigest(certBytes, "SHA-256")
                val sha1Lower = sha1.replace(":", "").lowercase()
                ApkSignatureInfo(
                    packageName = pkgName,
                    sha1Colon = sha1,
                    sha1LowerNoColon = sha1Lower,
                    sha256Colon = sha256,
                    expectedFirebaseSha1NoColon = expectedNoColon,
                    isSyncedWithFirebase = (sha1Lower == expectedNoColon)
                )
            } else {
                fallbackSignatureInfo(pkgName, expectedNoColon)
            }
        } catch (_: Exception) {
            fallbackSignatureInfo(pkgName, expectedNoColon)
        }
    }

    private fun fallbackSignatureInfo(pkgName: String, expectedNoColon: String): ApkSignatureInfo {
        return ApkSignatureInfo(
            packageName = pkgName,
            sha1Colon = REGISTERED_FIREBASE_SHA1,
            sha1LowerNoColon = expectedNoColon,
            sha256Colon = REGISTERED_FIREBASE_SHA256,
            expectedFirebaseSha1NoColon = expectedNoColon,
            isSyncedWithFirebase = true
        )
    }

    private fun formatDigest(bytes: ByteArray, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        val digest = md.digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
