package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.BuildConfig
import com.example.data.local.LocalAppSettings
import com.example.data.model.SharedSchedule
import com.example.domain.LunarCalendarEngine
import com.example.notifications.SmartReminderHelper
import com.example.ui.UiState
import com.example.util.ServerReleaseInfo
import com.example.util.SignatureSyncHelper
import com.example.widget.LunarCalendarAppWidgetProvider
import kotlinx.coroutines.launch

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
                        containerColor = MaterialTheme.colorScheme.primaryContainer
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
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                        containerColor = MaterialTheme.colorScheme.primaryContainer
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
    val coroutineScope = rememberCoroutineScope()
    var showGithubWorkflowCode by remember { mutableStateOf(false) }
    val signatureInfo = remember { SignatureSyncHelper.getRuntimeSignatureInfo(context) }

    // Server Update & SHA-256 Integrity Check states
    var customUpdateServerUrl by remember { mutableStateOf("") }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<ServerReleaseInfo?>(null) }

    var verifiedFileName by remember { mutableStateOf("") }
    var verifiedFileSha256 by remember { mutableStateOf("") }
    var expectedSha256Input by remember { mutableStateOf("") }
    var isComputingFileHash by remember { mutableStateOf(false) }

    // Automatically check server update on screen open
    LaunchedEffect(Unit) {
        isCheckingUpdate = true
        val res = SignatureSyncHelper.checkServerReleaseUpdate(context, customUpdateServerUrl)
        updateCheckResult = res
        if (res.expectedFileSha256.isNotBlank() && expectedSha256Input.isBlank()) {
            expectedSha256Input = res.expectedFileSha256
        }
        isCheckingUpdate = false
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isComputingFileHash = true
                val sha = SignatureSyncHelper.computeUriSha256(context, uri).orEmpty()
                verifiedFileName = uri.lastPathSegment ?: "file_kiem_tra.apk"
                verifiedFileSha256 = sha
                isComputingFileHash = false
                if (sha.isNotBlank()) {
                    onShowMessage("Đã tính xong mã băm SHA-256 của tệp!")
                } else {
                    onShowMessage("Không thể đọc tệp để tính SHA-256.")
                }
            }
        }
    }

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
      - name: Đồng bộ Chữ ký số (gradle/signing-sync.txt -> Debug & Release Keystore)
        run: |
          if [ -s "gradle/signing-sync.txt" ]; then
            tr -d '\n\r ' < gradle/signing-sync.txt | base64 -d > debug.keystore
          fi
          rm -f my-upload-key.jks
          keytool -importkeystore -srckeystore debug.keystore -destkeystore my-upload-key.jks \
            -srcstorepass android -deststorepass android \
            -srckeypass android -destkeypass android -srcalias androiddebugkey -destalias upload -noprompt
      - name: Tự động tăng phiên bản (1.1 -> 1.2.${'$'}{{ github.run_number }}) & Build APK + SHA-256
        env:
          KEYSTORE_PATH: ${'$'}{{ github.workspace }}/my-upload-key.jks
        run: |
          sed -i 's/compileSdk { version = release(36) { minorApiLevel = 1 } }/compileSdk = 36/g' app/build.gradle.kts || true
          gradle assembleRelease assembleDebug --no-daemon
          sha256sum app/build/outputs/apk/release/*.apk > app/build/outputs/apk/release/SHA256SUMS.txt
      - name: Publish Signed APK & SHA256 Manifest to Releases
        uses: softprops/action-gh-release@v2
        with:
          tag_name: v1.2.${'$'}{{ github.run_number }}
          name: "Lịch Vạn Niên v1.2.${'$'}{{ github.run_number }} (Signed APK + SHA256)"
          files: |
            app/build/outputs/apk/release/*.apk
            app/build/outputs/apk/release/SHA256SUMS.txt
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
                text = "Phiên bản v${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE}) • Đồng bộ tài khoản Google, kiểm tra bản cập nhật máy chủ và xác thực toàn vẹn SHA-256.",
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer
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
                            text = "Sao Lưu Đám Mây An Toàn (Tài Khoản Google)",
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
                            text = "Trạng thái: Đã đăng nhập & đồng bộ ($eventsCount sự kiện được bảo vệ)",
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
                            text = "Nhấn nút bên dưới để mở Trình chọn Tài khoản Google gốc của Android — chạm vào bất kỳ tài khoản Gmail nào trên máy là vào ngay lập tức.",
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

        // 2. Server Update Checker & SHA-256 File Integrity Verification Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdateAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kiểm Tra Phiên Bản Mới (Máy Chủ) & Kiểm Tra SHA-256",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tự động kiểm tra phiên bản mới từ máy chủ tốc độ cao (hoặc máy chủ CDN bên thứ ba) và xác thực mã băm SHA-256 để đảm bảo tệp không bị chỉnh sửa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customUpdateServerUrl,
                        onValueChange = { customUpdateServerUrl = it },
                        label = { Text("URL Máy chủ cập nhật bên thứ ba (Tùy chọn)") },
                        placeholder = { Text("https://cdn.ten-mien-cua-ban.com/version-manifest.json") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_update_server_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Status box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bản đang cài: v${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isCheckingUpdate) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val result = updateCheckResult
                            if (result != null) {
                                Text(
                                    text = if (result.hasNewerUpdate) {
                                        "🔔 Có bản mới trên máy chủ: v${result.latestVersionName} (${result.serverLabel})"
                                    } else {
                                        "✓ Trạng thái: ${result.releaseNotes}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (result.hasNewerUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (result.expectedFileSha256.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "SHA-256 chuẩn từ máy chủ: ${result.expectedFileSha256}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                Text(
                                    text = "Đang kiểm tra thông tin phiên bản từ máy chủ...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isCheckingUpdate = true
                                    val res = SignatureSyncHelper.checkServerReleaseUpdate(
                                        context,
                                        customUpdateServerUrl
                                    )
                                    updateCheckResult = res
                                    if (res.expectedFileSha256.isNotBlank()) {
                                        expectedSha256Input = res.expectedFileSha256
                                    }
                                    isCheckingUpdate = false
                                    onShowMessage(
                                        if (res.hasNewerUpdate) {
                                            "Đã tìm thấy phiên bản mới v${res.latestVersionName}!"
                                        } else {
                                            "Bạn đang sử dụng phiên bản mới nhất (v${BuildConfig.VERSION_NAME})."
                                        }
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("check_server_update_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kiểm tra bản mới")
                        }

                        OutlinedButton(
                            onClick = {
                                val portalUrl = updateCheckResult?.downloadPortalUrl.orEmpty()
                                val targetUrl = when {
                                    portalUrl.isNotBlank() -> portalUrl
                                    customUpdateServerUrl.isNotBlank() -> customUpdateServerUrl
                                    else -> "https://github.com/muitenvn/lich-am/releases"
                                }
                                SignatureSyncHelper.openExternalDownloadPortal(context, targetUrl)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_server_download_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mở Trang Tải")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // SHA-256 File Integrity Verification Section
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Xác Thực Mã Băm SHA-256 Chống Chỉnh Sửa Tệp:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "SHA-256 gói cài đặt hiện tại trên máy:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = signatureInfo.installedApkFileSha256,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = expectedSha256Input,
                        onValueChange = { expectedSha256Input = it.trim() },
                        label = { Text("Nhập mã SHA-256 chuẩn từ máy chủ để đối chiếu") },
                        placeholder = { Text("Dán chuỗi 64 ký tự hex SHA-256...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("expected_sha256_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_file_verify_sha256_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isComputingFileHash) "Đang tính..." else "Chọn tệp kiểm tra SHA-256")
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val hashToCopy = verifiedFileSha256.ifBlank { signatureInfo.installedApkFileSha256 }
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("apk-file-sha256", hashToCopy)
                                )
                                onShowMessage("Đã sao chép mã SHA-256 của tệp!")
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Sao chép SHA-256",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    val activeHashToCompare = verifiedFileSha256.ifBlank { signatureInfo.installedApkFileSha256 }
                    val cleanExpected = expectedSha256Input.replace(":", "").replace(" ", "").lowercase()
                    val cleanActual = activeHashToCompare.replace(":", "").replace(" ", "").lowercase()

                    if (verifiedFileSha256.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Tệp đã kiểm tra: $verifiedFileName",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "SHA-256: $verifiedFileSha256",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    if (cleanExpected.isNotBlank()) {
                        val isMatch = cleanExpected == cleanActual
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isMatch) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isMatch) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isMatch) {
                                        "✓ KHỚP 100% SHA-256: Tệp nguyên bản từ máy chủ, an toàn và chưa từng bị chỉnh sửa!"
                                    } else {
                                        "⚠ KHÔNG KHỚP SHA-256: Mã băm khác với chuỗi đối chiếu. Vui lòng kiểm tra lại nguồn tải!"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMatch) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. APK Signing Certificate Synchronization Card (Đồng bộ chữ ký số SHA-1 / SHA-256)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                            "✓ Đã đồng bộ 100% giữa Keystore ứng dụng (gradle/signing-sync.txt), Firebase OAuth (google-services.json) và GitHub Actions Release."
                        } else {
                            "⚠ Bản APK này được ký bằng khóa tạm (${signatureInfo.sha1Colon.take(14)}...). Bản cập nhật mới đã khóa cố định Keystore chuẩn trong gradle/signing-sync.txt và hỗ trợ Đăng nhập Google trực tiếp qua AccountManager."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
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
                                text = "SHA-1 đang chạy: ${signatureInfo.sha1Colon}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "SHA-1 chuẩn Firebase: ${SignatureSyncHelper.REGISTERED_FIREBASE_SHA1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "SHA-256 Chứng chỉ: ${signatureInfo.sha256Colon}",
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
                                val certPayload = "Package: ${signatureInfo.packageName}\nSHA-1: ${signatureInfo.sha1Colon}\nSHA-256: ${signatureInfo.sha256Colon}\nAPK-File-SHA256: ${signatureInfo.installedApkFileSha256}"
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

        // 4. Dark Mode & Theme Personalization Card
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
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
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

        // 5. Home Screen Widget Card
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
                        text = "Giao diện Sơn Mài Đỏ & Vàng Hoàng Kim mới: Hiển thị song song 2 ô Dương lịch & Âm lịch, Can Chi, Giờ Hoàng Đạo và nút làm mới nhanh ngay trên màn hình chính.",
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

        // 6. Automated APK Build & GitHub Release Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                        text = "Workflow `.github/workflows/build-and-release-apk.yml` tự động khôi phục Keystore chuẩn từ `gradle/signing-sync.txt` (không bao giờ sinh khóa ngẫu nhiên), tự động nâng phiên bản lên `1.2.<run_number>` và xuất kèm bảng mã băm `SHA256SUMS.txt` + `version-manifest.json`.",
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

