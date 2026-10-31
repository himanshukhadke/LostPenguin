package com.lostpenguin.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lostpenguin.app.data.ReelStat

class MainActivity : ComponentActivity() {

    private val viewModel: StatsViewModel by viewModels()

    private val overlayPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* permission result handled on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LostPenguinTheme {
                LostPenguinApp(
                    viewModel = viewModel,
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    hasOverlayPermission = Settings.canDrawOverlays(this)
                )
            }
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermLauncher.launch(intent)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

@Composable
fun LostPenguinApp(
    viewModel: StatsViewModel,
    onRequestOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    hasOverlayPermission: Boolean
) {
    val allStats by viewModel.allStats.collectAsState()
    val last7Days by viewModel.last7Days.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HeaderSection(todayCount = viewModel.todayCount) }

            if (!hasOverlayPermission) {
                item {
                    PermissionCard(
                        title = "Overlay Permission Required",
                        description = "Allow LostPenguin to show the counter over Instagram",
                        buttonText = "Grant Permission",
                        onClick = onRequestOverlayPermission
                    )
                }
            }

            item {
                PermissionCard(
                    title = "Enable Accessibility Service",
                    description = "LostPenguin needs accessibility access to detect reels",
                    buttonText = "Open Settings",
                    onClick = onOpenAccessibilitySettings
                )
            }

            item {
                Text(
                    text = "Last 7 Days",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item { WeekBarChart(last7Days) }

            item {
                Text(
                    text = "All Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(allStats) { stat ->
                StatRow(stat)
            }
        }
    }
}

@Composable
fun HeaderSection(todayCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Today", style = MaterialTheme.typography.labelLarge)
            Text(
                text = "$todayCount",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(text = "reels watched", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun WeekBarChart(stats: List<ReelStat>) {
    if (stats.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No data yet", color = Color.Gray)
        }
        return
    }

    val maxCount = stats.maxOf { it.count }.coerceAtLeast(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        stats.reversed().forEach { stat ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "${stat.count}", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .fillMaxHeight(stat.count.toFloat() / maxCount)
                        .padding(horizontal = 2.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small
                    ) {}
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stat.date.takeLast(5), // MM-DD
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun StatRow(stat: ReelStat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = stat.date, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "${stat.count} reels",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
}
