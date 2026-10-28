package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.LocalAppSettings
import com.example.data.model.SharedSchedule
import com.example.domain.LunarCalendarEngine
import com.example.notifications.SmartReminderHelper
import com.example.ui.UiState
import com.example.util.SignatureSyncHelper
import com.example.widget.LunarCalendarAppWidgetProvider

@Composable
fun ShareScheduleScreen(
    currentUserEmail: String,
    isSignedIn: Boolean = false,
    onRequestSignIn: () -> Unit = {},
    selectedDay: Int,
    selectedMonth: Int,
    selectedYear: Int,
    sharedWithMeState: UiState<List<SharedSchedule>>,
    sharedByMeState: UiState<List<SharedSchedule>>,
    onShareViaEmailCloud: (String, String, String, Int, Int, Int, String) -> Unit,
    onImportSharedSchedule: (SharedSchedule) -> Unit,
    onDeleteSharedSchedule: (String) -> Unit
) {
    val context = LocalContext.current
    var recipientEmail by remember { mutableStateOf("") }
    var shareTitle by remember { mutableStateOf("Họp mặt / Lịch gia đình") }
    var shareNotes by remember { mutableStateOf("Mời bạn tham gia đúng giờ Hoàng Đạo.") }

    val dayDetail = remember(selectedDay, selectedMonth, selectedYear) {
        LunarCalendarEngine.getDayDetail(selectedDay, selectedMonth, selectedYear)
    }

    val sharedWithMe = (sharedWithMeState as? UiState.Success)?.data ?: emptyList()
    val sharedByMe = (sharedByMeState as? UiState.Success)?.data ?: emptyList()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("share_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Chia Sẻ Lịch Trình Bảo Mật",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Chia sẻ nhanh qua Zalo, Messenger, Facebook, Tin nhắn (không cần đăng nhập) hoặc gửi qua Email Đám Mây khi đã đăng nhập.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Composer Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Ngày đang chọn: ${dayDetail.solarDay}/${dayDetail.solarMonth}/${dayDetail.solarYear} (${dayDetail.lunarDate.day}/${dayDetail.lunarDate.month} Âm lịch)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = recipientEmail,
                        onValueChange = { recipientEmail = it },
                        label = { Text("Email tài khoản Google người nhận (Tùy chọn)") },
                        placeholder = { Text("banbe@gmail.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_recipient_email_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = shareTitle,
                        onValueChange = { shareTitle = it },
                        label = { Text("Tiêu đề lịch trình") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_title_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = shareNotes,
                        onValueChange = { shareNotes = it },
                        label = { Text("Lời nhắn / Ghi chú") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                SmartReminderHelper.shareScheduleViaPlatform(
                                    context = context,
                                    title = shareTitle,
                                    solarDateDisplay = "${dayDetail.dayOfWeekName}, ${dayDetail.solarDay}/${dayDetail.solarMonth}/${dayDetail.solarYear}",
                                    lunarDateDisplay = "${dayDetail.lunarDate.day}/${dayDetail.lunarDate.month}/${dayDetail.lunarDate.year} (${dayDetail.canChiYear})",
                                    notes = shareNotes,
                                    targetEmail = recipientEmail
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_social_platform_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mạng xã hội / App")
                        }

                        OutlinedButton(
                            onClick = {
                                if (!isSignedIn) {
                                    onRequestSignIn()
                                } else if (recipientEmail.contains("@") && shareTitle.isNotBlank()) {
                                    onShareViaEmailCloud(
                                        recipientEmail,
                                        shareTitle,
                                        shareNotes,
                                        selectedDay,
                                        selectedMonth,
                                        selectedYear,
                                        "personal"
                                    )
                                    recipientEmail = ""
                                }
                            },
                            enabled = !isSignedIn || (recipientEmail.contains("@") && shareTitle.isNotBlank()),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("send_cloud_email_share_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isSignedIn) "Gửi Email Cloud" else "Email Cloud (Cần ĐN)")
                        }
                    }
                }
            }
        }

        // Inbox: Shared with me
        item {
            Text(
                text = if (isSignedIn) {
                    "Lịch bạn bè gửi tới ($currentUserEmail) (${sharedWithMe.size})"
                } else {
                    "Hộp thư Lịch Trình Đám Mây (Yêu cầu đăng nhập)"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (!isSignedIn) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Đăng nhập tài khoản Google để nhận và gửi lịch trình trực tiếp qua đám mây với bạn bè.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = onRequestSignIn,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Đăng nhập")
                        }
                    }
                }
            }
        } else if (sharedWithMe.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = "Chưa có lịch trình nào được chia sẻ tới email của bạn.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(sharedWithMe, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Từ: ${item.ownerName} • Dương: ${item.solarDateIso} (${item.lunarDateDisplay})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (item.notes.isNotBlank()) {
                                Text(
                                    text = item.notes,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        Button(
                            onClick = { onImportSharedSchedule(item) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lưu lịch")
                        }
                    }
                }
            }
        }

        // Outbox: Shared by me
        if (sharedByMe.isNotEmpty()) {
            item {
                Text(
                    text = "Lịch tôi đã chia sẻ (${sharedByMe.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(sharedByMe, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.title} → ${item.recipientEmail}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${item.solarDateIso} • ${item.lunarDateDisplay}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteSharedSchedule(item.id) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Thu hồi",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAndCloudScreen(
    userEmail: String,
    userName: String,
    isSignedIn: Boolean = false,
    onRequestSignIn: () -> Unit = {},
    localSettings: LocalAppSettings,
    eventsCount: Int,
    onUpdateThemeMode: (String) -> Unit,
    onUpdateAccentPalette: (String) -> Unit,
    onUpdateNotifyHolidays: (Boolean) -> Unit,
    onUpdateNotifyMung1Ram: (Boolean) -> Unit,
    onBackupSettingsToCloud: () -> Unit,
    onPinWidget: () -> Unit,
    onSignOut: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var showGithubWorkflowCode by remember { mutableStateOf(false) }
    val signatureInfo = remember { SignatureSyncHelper.getRuntimeSignatureInfo(context) }

    val githubWorkflowSnippet = remember {
        """
name: Build and Release Signed APK
on:
  push:
    branches: [ main, master ]
    tags: [ 'v*' ]
  workflow_dispatch:
permissions:
  contents: write
  actions: read
jobs:
  build-and-release:
    runs-on: ubuntu-latest
    env:
      STORE_PASSWORD: android
      KEY_PASSWORD: android
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
      - name: Cấu hình Android SDK (API 36)
        run: |
          SDKMANAGER=${'$'}(find "${'$'}{ANDROID_HOME:-/usr/local/lib/android/sdk}/cmdline-tools" -name sdkmanager | tail -n 1)
          yes | "${'$'}SDKMANAGER" --licenses > /dev/null || true
          "${'$'}SDKMANAGER" --install "platform-tools" "build-tools;36.0.0" "platforms;android-36" || true
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '9.3.1'
      - name: Đồng bộ Chữ ký số (Debug & Release Keystore)
        run: |
          base64 -d debug.keystore.base64 > debug.keystore
          rm -f my-upload-key.jks
          keytool -importkeystore -srckeystore debug.keystore -destkeystore my-upload-key.jks \
            -srcstorepass android -deststorepass android \
            -srckeypass android -destkeypass android -srcalias androiddebugkey -destalias upload -noprompt
      - name: Tự động tăng phiên bản (1.1 -> 1.2.${'$'}{{ github.run_number }}) & Build APK
        env:
          KEYSTORE_PATH: ${'$'}{{ github.workspace }}/my-upload-key.jks
        run: |
          sed -i 's/compileSdk { version = release(36) { minorApiLevel = 1 } }/compileSdk = 36/g' app/build.gradle.kts || true
          gradle assembleRelease assembleDebug --no-daemon
      - name: Publish Signed APK to GitHub Releases
        uses: softprops/action-gh-release@v2
        with:
          tag_name: v1.2.${'$'}{{ github.run_number }}
          name: "Lịch Vạn Niên v1.2.${'$'}{{ github.run_number }} (Signed APK)"
          files: |
            app/build/outputs/apk/release/*.apk
            app/build/outputs/apk/debug/*.apk
        env:
          GITHUB_TOKEN: ${'$'}{{ secrets.GITHUB_TOKEN }}
        """.trimIndent()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Cá Nhân Hóa & Sao Lưu Đám Mây",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Phiên bản ứng dụng 1.1 • Tùy chỉnh chế độ tối tiết kiệm pin, màu sắc truyền thống, đồng bộ chữ ký số APK và xuất bản tự động lên GitHub Release.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Cloud Backup & Account Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sao Lưu Đám Mây An Toàn (Firebase Firestore)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isSignedIn) {
                        Text(
                            text = "Tài khoản: ${userName.ifBlank { "Người dùng Google" }} ($userEmail)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Trạng thái: Đang đồng bộ thời gian thực ($eventsCount sự kiện đã lưu an toàn)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onBackupSettingsToCloud,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cloud_backup_now_btn"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đồng bộ cấu hình")
                            }
                            OutlinedButton(
                                onClick = onSignOut,
                                modifier = Modifier.testTag("sign_out_btn"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đăng xuất")
                            }
                        }
                    } else {
                        Text(
                            text = "Trạng thái: Đang dùng Chế độ Không Đăng Nhập ($eventsCount sự kiện lưu trên thiết bị).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Bạn không bắt buộc đăng nhập để xem lịch và tạo nhắc nhở trên máy. Đăng nhập Google bất cứ lúc nào để sao lưu đám mây.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onRequestSignIn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_sign_in_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Đăng nhập bằng Google để Sao lưu Đám mây")
                        }
                    }
                }
            }
        }

        // 2. APK Signing Certificate Synchronization Card (Đồng bộ chữ ký số SHA-1 / SHA-256)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Đồng Bộ Chữ Ký Số APK (SHA-1 & SHA-256)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (signatureInfo.isSyncedWithFirebase) {
                            "✓ Đã đồng bộ 100% giữa Keystore ứng dụng, Firebase OAuth (google-services.json) và GitHub Actions Release."
                        } else {
                            "Chứng chỉ APK đang chạy được đối chiếu trực tiếp với cấu hình Firebase."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Package: ${signatureInfo.packageName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "SHA-1: ${signatureInfo.sha1Colon}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "SHA-256: ${signatureInfo.sha256Colon}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val certPayload = "Package: ${signatureInfo.packageName}\nSHA-1: ${signatureInfo.sha1Colon}\nSHA-256: ${signatureInfo.sha256Colon}"
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("apk-signing-fingerprints", certPayload)
                                )
                                onShowMessage("Đã sao chép chữ ký số SHA-1 & SHA-256 vào bộ nhớ tạm!")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_signature_sha_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sao chép SHA-1/256")
                        }
                    }
                }
            }
        }

        // 3. Dark Mode & Theme Personalization Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chế Độ Tối Tiết Kiệm Pin & Giao Diện",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    val modes = listOf(
                        "dark" to "Tối OLED (Tiết kiệm pin)",
                        "light" to "Sáng Giấy Dó",
                        "system" to "Theo hệ thống"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        modes.forEach { (key, label) ->
                            FilterChip(
                                selected = localSettings.themeMode == key,
                                onClick = { onUpdateThemeMode(key) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("theme_mode_$key")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bảng màu văn hóa Việt Nam:",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    val palettes = listOf(
                        Triple("crimson_gold", "Son Đỏ & Vàng", Color(0xFFD32F2F)),
                        Triple("jade_bamboo", "Ngọc Bích & Tre", Color(0xFF2E7D32)),
                        Triple("indigo_moon", "Lam Nguyệt", Color(0xFF3949AB)),
                        Triple("royal_lotus", "Sen Hồng", Color(0xFFC2185B))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        palettes.forEach { (key, name, swatchColor) ->
                            val isSelected = localSettings.accentPalette == key
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (isSelected) Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(14.dp)
                                        ) else Modifier
                                    )
                                    .clickable { onUpdateAccentPalette(key) }
                                    .testTag("palette_$key")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tự động nhắc ngày Mùng 1 & Rằm hàng tháng")
                        Switch(
                            checked = localSettings.notifyMung1Ram,
                            onCheckedChange = onUpdateNotifyMung1Ram
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Thông báo ngày Lễ Tết Âm - Dương lịch")
                        Switch(
                            checked = localSettings.notifyHolidays,
                            onCheckedChange = onUpdateNotifyHolidays
                        )
                    }
                }
            }
        }

        // 4. Home Screen Widget Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Widget Lịch Âm Dương Ngoài Màn Hình Chính",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Xem nhanh ngày Dương lịch, ngày Âm lịch, Can Chi, Tiết Khí và Ngày Lễ ngay trên màn hình chính Android mà không cần mở ứng dụng.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onPinWidget,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pin_widget_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Ghim Widget ra Màn hình")
                        }
                        OutlinedButton(
                            onClick = {
                                LunarCalendarAppWidgetProvider.refreshAllWidgets(context)
                                onShowMessage("Đã làm mới dữ liệu Widget Lịch Vạn Niên!")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Làm mới Widget")
                        }
                    }
                }
            }
        }

        // 5. Automated APK Build & GitHub Release Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tự Động Xuất APK (Đồng Bộ Chữ Ký) Lên GitHub Release",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Workflow `.github/workflows/build-and-release-apk.yml` tự động chạy ngay khi bạn Push lên nhánh `main`/`master` (hoặc bấm Run workflow), giải mã `debug.keystore.base64` để đồng bộ chữ ký SHA-1/SHA-256 cho cả bản Release & Debug APK rồi đăng lên GitHub Releases.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("build-and-release-apk.yml", githubWorkflowSnippet)
                                )
                                onShowMessage("Đã sao chép cấu hình .github/workflows/build-and-release-apk.yml!")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_github_workflow_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sao chép YAML")
                        }

                        OutlinedButton(
                            onClick = { showGithubWorkflowCode = !showGithubWorkflowCode },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("toggle_github_workflow_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (showGithubWorkflowCode) "Ẩn cấu hình" else "Xem file YAML")
                        }
                    }

                    if (showGithubWorkflowCode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0E111A),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = githubWorkflowSnippet,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFFE6EDF3),
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
