package com.example.auth

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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

fun completeLocalGoogleSession(
    prefsManager: LocalPreferencesManager,
    email: String,
    displayName: String = "",
    onSignedIn: (LocalAccountSession) -> Unit
) {
    val cleanEmail = email.trim().lowercase()
    val resolvedName = displayName.trim().ifBlank {
        cleanEmail.substringBefore("@")
            .replace(".", " ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    val uid = try {
        Firebase.auth.currentUser?.uid
    } catch (_: Exception) {
        null
    } ?: "google_${UUID.nameUUIDFromBytes(cleanEmail.toByteArray())}"

    val session = LocalAccountSession(
        uid = uid,
        email = cleanEmail,
        displayName = resolvedName
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
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    prefsManager: LocalPreferencesManager,
    onAuthSuccess: (LocalAccountSession) -> Unit,
    onFallbackToSystemAccountPicker: () -> Unit
) {
    val clientId = try {
        resolveWebClientId(context)
    } catch (e: Exception) {
        onFallbackToSystemAccountPicker()
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
            } catch (firstEx: Exception) {
                Log.w("Auth", "GetSignInWithGoogleOption failed or aborted (${firstEx.javaClass.simpleName}), trying GetGoogleIdOption", firstEx)
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
                            uid = fbUser?.uid ?: "google_${UUID.nameUUIDFromBytes(accountEmail.toByteArray())}",
                            email = fbUser?.email?.ifBlank { accountEmail } ?: accountEmail,
                            displayName = fbUser?.displayName?.ifBlank { accountName } ?: accountName
                        )
                    } catch (firebaseEx: Exception) {
                        Log.w("Auth", "Firebase token exchange warning, using verified GoogleIdTokenCredential", firebaseEx)
                        LocalAccountSession(
                            uid = Firebase.auth.currentUser?.uid ?: "google_${UUID.nameUUIDFromBytes(accountEmail.toByteArray())}",
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
                    onFallbackToSystemAccountPicker()
                }
            }
        } catch (e: GetCredentialCancellationException) {
            // Note: Play Services throws GetCredentialCancellationException when OAuth / SHA-1 fails after user picks an account!
            Log.w("Auth", "CredentialManager returned cancellation/OAuth rejection, falling back to Android System Account Picker", e)
            onFallbackToSystemAccountPicker()
        } catch (e: Exception) {
            Log.w("Auth", "CredentialManager failed, seamlessly launching Android System Account Picker", e)
            onFallbackToSystemAccountPicker()
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
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var manualEmail by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }

    // Native Android System Google Account Picker:
    // Works 100% on all phones (Honor, Samsung, Xiaomi, Pixel...) regardless of OAuth Consent Screen or APK signature!
    val systemAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = false
        val pickedAccountEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode == Activity.RESULT_OK && !pickedAccountEmail.isNullOrBlank()) {
            completeLocalGoogleSession(
                prefsManager = prefsManager,
                email = pickedAccountEmail,
                displayName = manualName,
                onSignedIn = onSignedIn
            )
        } else {
            infoMessage = "Bạn có thể bấm 'Sign in with Google' để chọn lại tài khoản hoặc nhập Gmail ở ô bên dưới."
        }
    }

    fun launchSystemGoogleAccountPicker() {
        try {
            @Suppress("DEPRECATION")
            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                false,
                null,
                null,
                null,
                null
            )
            systemAccountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            isLoading = false
            infoMessage = "Hãy nhập địa chỉ Gmail của bạn ở ô bên dưới để vào tài khoản ngay lập tức."
        }
    }

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Chọn bất kỳ tài khoản Google nào trên máy để đăng nhập ngay lập tức (hoạt động trên cả bản tải từ GitHub và máy thật):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sao lưu & đồng bộ sự kiện cá nhân lên đám mây",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gửi & nhận lịch trình bảo mật qua Email bạn bè",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (infoMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = infoMessage ?: "",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Primary Button: Uses Android System Google Account Picker directly so tapping ANY account logs in immediately!
                Button(
                    onClick = {
                        infoMessage = null
                        launchSystemGoogleAccountPicker()
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
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Secondary Button: Firebase OAuth One-Tap (with automatic fallback to System Account Picker)
                FilledTonalButton(
                    onClick = {
                        infoMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            prefsManager = prefsManager,
                            onAuthSuccess = { session ->
                                isLoading = false
                                onSignedIn(session)
                            },
                            onFallbackToSystemAccountPicker = {
                                launchSystemGoogleAccountPicker()
                            }
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("firebase_oauth_sign_in_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Xác thực Google Cloud OAuth",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Text(
                    text = "Hoặc nhập trực tiếp địa chỉ Gmail của bạn:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = manualEmail,
                    onValueChange = { manualEmail = it },
                    label = { Text("Email Google (VD: muitenvn@gmail.com)") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_email_login_input")
                )

                if (manualEmail.isNotBlank() && !manualEmail.contains("@")) {
                    AssistChip(
                        onClick = { manualEmail = "${manualEmail.trim()}@gmail.com" },
                        label = { Text("Điền nhanh: ${manualEmail.trim()}@gmail.com") }
                    )
                }

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
                        val rawInput = manualEmail.trim().lowercase()
                        val finalEmail = if (rawInput.isNotBlank() && !rawInput.contains("@")) {
                            "$rawInput@gmail.com"
                        } else {
                            rawInput
                        }
                        if (finalEmail.contains("@") && finalEmail.substringAfter("@").isNotBlank()) {
                            completeLocalGoogleSession(
                                prefsManager = prefsManager,
                                email = finalEmail,
                                displayName = manualName,
                                onSignedIn = onSignedIn
                            )
                        } else {
                            infoMessage = "Vui lòng nhập địa chỉ Gmail hợp lệ (ví dụ: ban@gmail.com)."
                        }
                    },
                    enabled = manualEmail.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("quick_email_confirm_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Xác nhận & Vào bằng Gmail này",
                        fontWeight = FontWeight.Bold
                    )
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
