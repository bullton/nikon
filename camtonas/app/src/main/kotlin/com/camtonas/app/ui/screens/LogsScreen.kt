package com.camtonas.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.camtonas.app.sync.SyncEvent
import com.camtonas.app.ui.SyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    vm: SyncViewModel = viewModel()
) {
    val entries = remember { mutableStateListOf<String>() }
    val running by vm.running.collectAsState()

    // The VM only exposes a single last event, so we just keep
    // updating the list. (A real implementation would expose a
    // SharedFlow on the VM.)
    val last by vm.lastEvent.collectAsState()
    LaunchedEffect(last) {
        last?.let { entries.add(0, describe(it)) }
        while (entries.size > 500) entries.removeLast()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志 (${entries.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { entries.clear() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空")
                    }
                }
            )
        }
    ) { p ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(p),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text("还没有日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val state = rememberLazyListState()
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize().padding(p).padding(horizontal = 8.dp)
            ) {
                items(entries) { line ->
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun describe(e: SyncEvent): String {
    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        .format(java.util.Date())
    return when (e) {
        is SyncEvent.Idle        -> "[$time] idle: ${e.message}"
        is SyncEvent.Connecting  -> "[$time] connect ${e.target}"
        is SyncEvent.Connected   -> "[$time] connected (${e.info})"
        is SyncEvent.Disconnected-> "[$time] disconnected (${e.reason})"
        is SyncEvent.FoundNew    -> "[$time] found ${e.count}"
        is SyncEvent.Downloading -> "[$time] ↓ ${e.name} (${e.size/1024} KB)"
        is SyncEvent.Downloaded  -> "[$time] ✓ dl ${e.name} in ${e.ms}ms"
        is SyncEvent.Uploading   -> "[$time] ↑ ${e.name} (${e.size/1024} KB)"
        is SyncEvent.Uploaded    -> "[$time] ✓ up ${e.name} in ${e.ms}ms"
        is SyncEvent.Skipped     -> "[$time] skip ${e.name}: ${e.reason}"
        is SyncEvent.Error       -> "[$time] ✗ [${e.where}] ${e.cause.message ?: "?"}"
        is SyncEvent.Log         -> "[$time] [${e.level}] ${e.message}"
    }
}
