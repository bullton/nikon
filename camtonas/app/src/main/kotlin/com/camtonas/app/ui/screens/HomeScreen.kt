package com.camtonas.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.camtonas.app.ui.SyncViewModel
import com.camtonas.app.sync.SyncEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    vm: SyncViewModel = viewModel()
) {
    val running by vm.running.collectAsState()
    val lastEvent by vm.lastEvent.collectAsState()
    val stats by vm.stats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CamToNAS") },
                actions = {
                    IconButton(onClick = onOpenLogs) {
                        Icon(Icons.Outlined.History, contentDescription = "日志")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(running = running, lastEvent = lastEvent, stats = stats)
            BigActionButton(running = running, onToggle = { vm.toggle() })
            QuickHelp()
        }
    }
}

@Composable
private fun StatusCard(running: Boolean, lastEvent: SyncEvent?, stats: SyncViewModel.Stats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (running) Icons.Filled.PlayArrow else Icons.Filled.Stop,
                    contentDescription = null,
                    tint = if (running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    if (running) "正在监听相机" else "未启动",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                lastEvent?.let { describe(it) } ?: "开启后,程序会在后台轮询相机并把新照片上传到 NAS。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat("已上传", stats.uploaded.toString())
                Stat("下载中", stats.downloading.toString())
                Stat("失败", stats.failed.toString())
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BigActionButton(running: Boolean, onToggle: () -> Unit) {
    Button(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (running) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            if (running) "停止同步" else "开始同步",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun QuickHelp() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("使用说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("1. 先到【设置】配置 NAS 地址、账号密码")
            Text("2. 相机:菜单 → 设定菜单 → 连接至智能设备,打开 WiFi")
            Text("3. 把手机连到相机的 WiFi (默认 NIKON_Z_30_xxxxxx)")
            Text("4. 返回主页,点【开始同步】")
            Text("5. 拍完照,程序会在后台把新 JPG 上传到 NAS")
        }
    }
}

private fun describe(e: SyncEvent): String = when (e) {
    is SyncEvent.Idle        -> e.message.ifEmpty { "空闲" }
    is SyncEvent.Connecting  -> "正在连接 ${e.target}…"
    is SyncEvent.Connected   -> "已连接: ${e.info}"
    is SyncEvent.Disconnected-> "已断开: ${e.reason}"
    is SyncEvent.FoundNew    -> "发现 ${e.count} 个新文件"
    is SyncEvent.Downloading -> "下载中: ${e.name} (${e.size / 1024} KB)"
    is SyncEvent.Downloaded  -> "已下载 ${e.name} (${e.ms} ms)"
    is SyncEvent.Uploading   -> "上传中: ${e.name} (${e.size / 1024} KB)"
    is SyncEvent.Uploaded    -> "✓ 已上传 ${e.name} (${e.ms} ms)"
    is SyncEvent.Skipped     -> "跳过 ${e.name}: ${e.reason}"
    is SyncEvent.Error       -> "✗ [${e.where}] ${e.cause.message ?: "未知错误"}"
    is SyncEvent.Log         -> "· ${e.message}"
}
