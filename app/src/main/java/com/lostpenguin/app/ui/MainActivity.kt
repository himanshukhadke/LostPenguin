package com.lostpenguin.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.lostpenguin.app.data.ReelStat
import com.lostpenguin.app.update.UpdateState
import com.lostpenguin.app.util.AccessibilityUtils
import com.lostpenguin.app.util.LogFileWriter
import com.lostpenguin.app.util.ScrollDebugLog
class MainActivity : ComponentActivity() {

    private val viewModel: StatsViewModel by viewModels()

    private val overlayPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions(this) }

    private val accessibilityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScrollDebugLog.init(this)
        viewModel.refreshPermissions(this)
        setContent {
            LostPenguinTheme {
                LostPenguinApp(
                    viewModel = viewModel,
                    onRequestOverlayPermission = {
                        overlayPermLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"))
                        )
                    },
                    onOpenAccessibilitySettings = {
                        accessibilityLauncher.launch(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        )
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions(this)
    }
}

@Composable
fun LostPenguinApp(
    viewModel: StatsViewModel,
    onRequestOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val allStats by viewModel.allStats.collectAsState()
    val last7Days by viewModel.last7Days.collectAsState()
    val hasOverlay by viewModel.hasOverlayPermission.collectAsState()
    val hasAccessibility by viewModel.hasAccessibilityPermission.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1A1A2E)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("📊") },
                    label = { Text("Stats") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("🐛") },
                    label = { Text("Debug") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> StatsScreen(
                modifier = Modifier.padding(padding),
                allStats = allStats,
                last7Days = last7Days,
                hasOverlay = hasOverlay,
                hasAccessibility = hasAccessibility,
                updateState = updateState,
                todayCount = viewModel.todayCount,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onStartUpdate = { viewModel.startUpdate(it) }
            )
            1 -> DebugScreen(modifier = Modifier.padding(padding))
        }
    }
}

@Composable
fun StatsScreen(
    modifier: Modifier,
    allStats: List<ReelStat>,
    last7Days: List<ReelStat>,
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    updateState: UpdateState,
    todayCount: Int,
    onRequestOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStartUpdate: (Context) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HeaderSection(todayCount = todayCount) }

            // Update banner — only show when update available or downloading
            if (updateState !is UpdateState.Idle) {
                item { UpdateBanner(updateState = updateState, onStartUpdate = { onStartUpdate(context) }) }
            }

            if (!hasOverlay) {
                item {
                    PermissionCard(
                        title = "Overlay Permission Required",
                        description = "Allow LostPenguin to show the counter over Instagram",
                        buttonText = "Grant",
                        onClick = onRequestOverlayPermission
                    )
                }
            }

            if (!hasAccessibility) {
                item {
                    PermissionCard(
                        title = "Accessibility Service",
                        description = "Enable LostPenguin in Accessibility Settings to detect reels",
                        buttonText = "Enable",
                        onClick = onOpenAccessibilitySettings
                    )
                }
            }

            if (hasOverlay && hasAccessibility) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3A2A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("✓", fontSize = 20.sp, color = Color(0xFF4CAF50))
                            Column {
                                Text("All set!", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                Text(
                                    "LostPenguin is actively tracking your reels",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4CAF50).copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            item { Text("Last 7 Days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item { WeekBarChart(last7Days) }
            item { Text("All Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

            if (allStats.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No data yet. Start watching reels!", color = Color.Gray)
                    }
                }
            } else {
                items(allStats) { stat -> StatRow(stat) }
            }
        }
    }
}

@Composable
fun DebugScreen(modifier: Modifier) {
    var logs by remember { mutableStateOf(ScrollDebugLog.getLogs()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(Unit) {
        val listener = { logs = ScrollDebugLog.getLogs() }
        ScrollDebugLog.addListener(listener)
        onDispose { ScrollDebugLog.removeListener(listener) }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Debug Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${logs.size} events", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val file = LogFileWriter.getFile()
                        if (file != null && file.exists()) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "LostPenguin Debug Log")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Logs"))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4D9FFF))
                ) {
                    Text("Share Logs", fontSize = 12.sp)
                }
                Button(
                    onClick = { ScrollDebugLog.clear() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444))
                ) {
                    Text("Clear", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Open Instagram → scroll reels → come back here",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (logs.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No events yet", color = Color.Gray)
                        }
                    }
                } else {
                    items(logs) { log ->
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = when {
                                log.contains("COUNTED") -> Color(0xFF4CAF50)
                                log.contains("SCROLL") -> Color(0xFF4D9FFF)
                                log.contains("ERROR") -> Color(0xFFFF3333)
                                log.contains("ignored") || log.contains("cancelled") || log.contains("back") -> Color(0xFFFF6B6B)
                                else -> Color.White.copy(alpha = 0.7f)
                            },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderSection(todayCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A2744), Color(0xFF0D1B3E))
                )
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Today",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF4D9FFF).copy(alpha = 0.8f)
            )
            Text(
                "$todayCount",
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4D9FFF)
            )
            Text(
                "reels watched",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun UpdateBanner(updateState: UpdateState, onStartUpdate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A0D))
    ) {
        when (updateState) {
            is UpdateState.Available -> {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Update Available", fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                        Text(
                            "${updateState.version} is ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFD700).copy(alpha = 0.7f)
                        )
                    }
                    Button(
                        onClick = onStartUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                    ) {
                        Text("Update", color = Color.Black)
                    }
                }
            }
            is UpdateState.Downloading -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Downloading update...", fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                        Text("${updateState.progress}%", color = Color(0xFFFFD700))
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { updateState.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFFFD700),
                        trackColor = Color(0xFF444400)
                    )
                }
            }
            is UpdateState.ReadyToInstall -> {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("✓ Download complete!", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    Button(
                        onClick = onStartUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Install Now")
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
fun PermissionCard(title: String, description: String, buttonText: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1A1A))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF6B6B))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun WeekBarChart(stats: List<ReelStat>) {
    if (stats.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No data yet", color = Color.Gray)
        }
        return
    }

    val maxCount = stats.maxOf { it.count }.coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            stats.reversed().forEach { stat ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Text("${stat.count}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(stat.count.toFloat() / maxCount)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF4D9FFF), Color(0xFF1A4A8A))
                                )
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stat.date.takeLast(5),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun StatRow(stat: ReelStat) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stat.date, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
        Text(
            "${stat.count} reels",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF4D9FFF)
        )
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
}
