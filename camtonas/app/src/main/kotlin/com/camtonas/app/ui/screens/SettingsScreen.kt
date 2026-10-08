package com.camtonas.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.camtonas.app.data.AppSettings
import com.camtonas.app.data.ConnectionMode
import com.camtonas.app.data.NasProtocol
import com.camtonas.app.ui.SyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SyncViewModel = viewModel()
) {
    val s by vm.settingsFlow.collectAsState(initial = AppSettings())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { p ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(p)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Section("连接方式") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = s.connectionMode == ConnectionMode.WIFI_PTP_IP,
                        onClick = { vm.updateSettings { it.copy(connectionMode = ConnectionMode.WIFI_PTP_IP) } },
                        label = { Text("WiFi (PTP/IP)") }
                    )
                    FilterChip(
                        selected = s.connectionMode == ConnectionMode.USB_MTP,
                        onClick = { vm.updateSettings { it.copy(connectionMode = ConnectionMode.USB_MTP) } },
                        label = { Text("USB (MTP)") }
                    )
                }
            }

            Section(if (s.connectionMode == ConnectionMode.WIFI_PTP_IP) "相机 IP / 端口" else "USB Bucket 名") {
                OutlinedTextField(
                    value = s.cameraIp,
                    onValueChange = { v -> vm.updateSettings { it.copy(cameraIp = v) } },
                    label = { Text(if (s.connectionMode == ConnectionMode.WIFI_PTP_IP) "相机 IP" else "Bucket 名称") },
                    placeholder = {
                        Text(if (s.connectionMode == ConnectionMode.WIFI_PTP_IP) "192.168.1.1" else "NIKON Z 30")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (s.connectionMode == ConnectionMode.WIFI_PTP_IP) {
                    OutlinedTextField(
                        value = s.cameraPort.toString(),
                        onValueChange = { v -> vm.updateSettings { it.copy(cameraPort = v.toIntOrNull() ?: 15740) } },
                        label = { Text("PTP 端口") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = s.pollIntervalSec.toString(),
                    onValueChange = { v -> vm.updateSettings { it.copy(pollIntervalSec = v.toIntOrNull() ?: 10) } },
                    label = { Text("轮询间隔(秒)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Section("上传文件类型") {
                ToggleRow("JPG", s.uploadJpeg) { v -> vm.updateSettings { it.copy(uploadJpeg = v) } }
                ToggleRow("RAW (NEF)", s.uploadRaw) { v -> vm.updateSettings { it.copy(uploadRaw = v) } }
                ToggleRow("视频 (MOV/MP4)", s.uploadVideo) { v -> vm.updateSettings { it.copy(uploadVideo = v) } }
                ToggleRow("按拍摄日期整理目录", s.organizeByDate) { v -> vm.updateSettings { it.copy(organizeByDate = v) } }
                ToggleRow("上传后从相机删除 (慎用)", s.deleteAfterUpload) { v -> vm.updateSettings { it.copy(deleteAfterUpload = v) } }
                ToggleRow("允许在流量网络下运行", s.runOnMeteredNetwork) { v -> vm.updateSettings { it.copy(runOnMeteredNetwork = v) } }
            }

            Section("NAS 协议") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(NasProtocol.SMB, NasProtocol.SFTP, NasProtocol.FTP, NasProtocol.WEBDAV).forEach { proto ->
                        FilterChip(
                            selected = s.nasProtocol == proto,
                            onClick = { vm.updateSettings { it.copy(nasProtocol = proto) } },
                            label = { Text(proto.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = s.nasHost,
                    onValueChange = { v -> vm.updateSettings { it.copy(nasHost = v) } },
                    label = { Text("NAS 地址 (IP / 域名)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (s.nasProtocol != NasProtocol.WEBDAV && s.nasProtocol != NasProtocol.SMB) {
                    OutlinedTextField(
                        value = if (s.nasPort > 0) s.nasPort.toString() else "",
                        onValueChange = { v -> vm.updateSettings { it.copy(nasPort = v.toIntOrNull() ?: 0) } },
                        label = { Text("端口 (留空用默认)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (s.nasProtocol == NasProtocol.SMB) {
                    OutlinedTextField(
                        value = s.nasShare,
                        onValueChange = { v -> vm.updateSettings { it.copy(nasShare = v) } },
                        label = { Text("SMB 共享名 (Share)") },
                        placeholder = { Text("photos") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = s.nasPath,
                    onValueChange = { v -> vm.updateSettings { it.copy(nasPath = v) } },
                    label = { Text("远程子目录 (例 /Photos)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = s.nasUsername,
                    onValueChange = { v -> vm.updateSettings { it.copy(nasUsername = v) } },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = s.nasPassword,
                    onValueChange = { v -> vm.updateSettings { it.copy(nasPassword = v) } },
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (s.nasProtocol == NasProtocol.FTP || s.nasProtocol == NasProtocol.WEBDAV) {
                    ToggleRow("使用 TLS / SSL (FTPS / HTTPS)", s.useTls) { v -> vm.updateSettings { it.copy(useTls = v) } }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        content()
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.padding(end = 16.dp))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
