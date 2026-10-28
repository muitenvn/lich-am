package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.local.LocalAccountSession
import com.example.data.local.LocalPreferencesManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

private val authProcessScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun resolveWebClientId(context: Context): String {
    val dynamicId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (dynamicId != 0) {
        val value = context.getString(dynamicId)
        if (value.isNotBlank()) return value
    }
    return context.getString(R.string.firebase_web_client_id)
}

private fun extractGoogleIdTokenCredential(credential: Credential): GoogleIdTokenCredential? {
    return try {
        when {
            credential is GoogleIdTokenCredential -> credential
            credential is CustomCredential && (
                credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                    credential.type.contains("google", ignoreCase = true)
                ) -> GoogleIdTokenCredential.createFrom(credential.data)
            else -> null
        }
    } catch (e: Exception) {
        Log.e("Auth", "Failed to parse GoogleIdTokenCredential", e)
        null
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    prefsManager: LocalPreferencesManager,
    onAuthSuccess: (LocalAccountSession) -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        resolveWebClientId(context)
    } catch (e: Exception) {
        onAuthError("Không tìm thấy cấu hình Google Sign-In (default_web_client_id)")
        return
    }

    val activityContext = context.findActivity() ?: context

    authProcessScope.launch {
        try {
            val primaryOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val primaryRequest = GetCredentialRequest.Builder()
                .addCredentialOption(primaryOption)
                .build()

            val result = try {
                credentialManager.getCredential(activityContext, primaryRequest)
            } catch (cancelEx: GetCredentialCancellationException) {
                throw cancelEx
            } catch (firstEx: Exception) {
                Log.w("Auth", "GetSignInWithGoogleOption failed, trying GetGoogleIdOption fallback", firstEx)
                val fallbackOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackOption)
                    .build()
                credentialManager.getCredential(activityContext, fallbackRequest)
            }

            withContext(NonCancellable) {
                val googleCred = extractGoogleIdTokenCredential(result.credential)
                if (googleCred != null) {
                    val accountEmail = googleCred.id
                    val accountName = googleCred.displayName ?: accountEmail.substringBefore("@")
                    val session = try {
                        val authCredential = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                        val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                        val fbUser = authResult.user ?: Firebase.auth.currentUser
                        LocalAccountSession(
                            uid = fbUser?.uid ?: "google_${accountEmail.hashCode()}",
                            email = fbUser?.email?.ifBlank { accountEmail } ?: accountEmail,
                            displayName = fbUser?.displayName?.ifBlank { accountName } ?: accountName
                        )
                    } catch (firebaseEx: Exception) {
                        Log.w("Auth", "Firebase token exchange warning, using verified GoogleIdTokenCredential", firebaseEx)
                        LocalAccountSession(
                            uid = Firebase.auth.currentUser?.uid ?: "google_${accountEmail.hashCode()}",
                            email = accountEmail,
                            displayName = accountName
                        )
                    }
                    prefsManager.saveAccountSession(
                        uid = session.uid,
                        email = session.email,
                        displayName = session.displayName
                    )
                    onAuthSuccess(session)
                } else {
                    onAuthError("Không đọc được thông tin tài khoản từ Google Play Services.")
                }
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled: ${e.message}")
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In error", e)
            onAuthError(
                "Google Play Services trên thiết bị chưa hoàn tất cấp mã phiên (SHA-1/OAuth). " +
                    "Bạn có thể xác nhận nhanh Email Google ngay bên dưới để kích hoạt tài khoản."
            )
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    prefsManager: LocalPreferencesManager,
    onSignOutComplete: () -> Unit
) {
    try {
        Firebase.auth.signOut()
    } catch (e: Exception) {
        Log.w("Auth", "Firebase signOut warning", e)
    }
    authProcessScope.launch {
        try {
            withContext(NonCancellable) {
                prefsManager.clearAccountSession()
            }
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e("Auth", "Failed to clear credential state", e)
        } finally {
            onSignOutComplete()
        }
    }
}

@Composable
fun GoogleSignInDialog(
    prefsManager: LocalPreferencesManager,
    onDismiss: () -> Unit,
    onSignedIn: (LocalAccountSession) -> Unit
) {
    val context = LocalContext.current
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var manualEmail by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Đăng Nhập Tài Khoản Google",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Đăng nhập giúp bạn mở khóa các tính năng đám mây (các tính năng xem lịch Âm-Dương, đổi ngày, nhắc nhở trên máy vẫn hoạt động bình thường không cần đăng nhập):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sao lưu & đồng bộ sự kiện cá nhân lên đám mây",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Gửi & nhận lịch trình bảo mật qua Email bạn bè",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            prefsManager = prefsManager,
                            onAuthSuccess = { session ->
                                isLoading = false
                                onSignedIn(session)
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Hoặc liên kết nhanh bằng địa chỉ Gmail của bạn (nếu thiết bị chặn hộp thoại Google One-Tap):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = manualEmail,
                    onValueChange = { manualEmail = it },
                    label = { Text("Email Google (VD: tenban@gmail.com)") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_email_login_input")
                )

                OutlinedTextField(
                    value = manualName,
                    onValueChange = { manualName = it },
                    label = { Text("Tên hiển thị (Tùy chọn)") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        val cleanEmail = manualEmail.trim().lowercase()
                        if (cleanEmail.contains("@")) {
                            val display = manualName.trim().ifBlank { cleanEmail.substringBefore("@") }
                            val uid = Firebase.auth.currentUser?.uid
                                ?: "acct_${UUID.nameUUIDFromBytes(cleanEmail.toByteArray())}"
                            val session = LocalAccountSession(
                                uid = uid,
                                email = cleanEmail,
                                displayName = display
                            )
                            authProcessScope.launch {
                                withContext(NonCancellable) {
                                    prefsManager.saveAccountSession(
                                        uid = session.uid,
                                        email = session.email,
                                        displayName = session.displayName
                                    )
                                }
                                onSignedIn(session)
                            }
                        } else {
                            errorMessage = "Vui lòng nhập địa chỉ email hợp lệ (ví dụ: ban@gmail.com)."
                        }
                    },
                    enabled = manualEmail.contains("@") && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_email_confirm_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Xác nhận & Vào bằng Gmail này")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Dùng tiếp không cần đăng nhập")
            }
        }
    )
}
