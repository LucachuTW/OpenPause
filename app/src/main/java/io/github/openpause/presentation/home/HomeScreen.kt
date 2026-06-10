package io.github.openpause.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.openpause.R
import io.github.openpause.core.permissions.RequiredPermissionStatus
import io.github.openpause.domain.usecase.RuleConstraints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun HomeScreen(
    permissionStatus: RequiredPermissionStatus,
    onRefreshPermissionStatus: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editingApp by remember { mutableStateOf<AppRuleUiModel?>(null) }

    HomeContent(
        uiState = uiState,
        permissionStatus = permissionStatus,
        onRefreshPermissionStatus = onRefreshPermissionStatus,
        onQueryChange = viewModel::updateQuery,
        onRefreshApps = viewModel::refreshInstalledApps,
        onEnabledChange = viewModel::setEnabled,
        onEditTimers = { editingApp = it },
    )

    editingApp?.let { app ->
        TimerDialog(
            app = app,
            onDismiss = { editingApp = null },
            onSave = { waitSeconds, usageMinutes ->
                viewModel.saveTimers(app, waitSeconds, usageMinutes)
                editingApp = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    permissionStatus: RequiredPermissionStatus,
    onRefreshPermissionStatus: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRefreshApps: () -> Unit,
    onEnabledChange: (AppRuleUiModel, Boolean) -> Unit,
    onEditTimers: (AppRuleUiModel) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(onClick = onRefreshApps) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.home_refresh_apps),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                    )
                },
                placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
                shape = RoundedCornerShape(28.dp),
            )

            if (uiState.totalUsageMillis > 0L) {
                Text(
                    text = stringResource(
                        R.string.home_total_usage,
                        formatUsageDuration(uiState.totalUsageMillis),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            if (!permissionStatus.allGranted) {
                PermissionWarning(onClick = onRefreshPermissionStatus)
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.apps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (uiState.query.isBlank()) {
                            stringResource(R.string.home_no_apps)
                        } else {
                            stringResource(R.string.home_no_search_results)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = uiState.apps,
                        key = { it.packageName },
                    ) { app ->
                        AppRuleRow(
                            app = app,
                            maxUsageMillis = uiState.maxUsageMillis,
                            onEnabledChange = { enabled -> onEnabledChange(app, enabled) },
                            onEditTimers = { onEditTimers(app) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionWarning(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.home_permission_warning),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onClick) {
                Text(stringResource(R.string.action_refresh))
            }
        }
    }
}

@Composable
private fun AppRuleRow(
    app: AppRuleUiModel,
    maxUsageMillis: Long,
    onEnabledChange: (Boolean) -> Unit,
    onEditTimers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onEditTimers,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InstalledAppIcon(
                packageName = app.packageName,
                modifier = Modifier.size(44.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatUsageDuration(app.usageMillis),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (app.usageMillis > 0L) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                LinearProgressIndicator(
                    progress = {
                        if (maxUsageMillis <= 0L) 0f
                        else (app.usageMillis.toFloat() / maxUsageMillis.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
                Text(
                    text = stringResource(R.string.home_rule_summary, app.waitTimeSeconds, app.usageLimitMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = app.enabled,
                onCheckedChange = onEnabledChange,
            )
        }
    }
}

private const val WaitWheelStepSeconds = 5
private const val WheelVisibleRows = 5
private const val WheelPadRows = WheelVisibleRows / 2
private val WheelItemHeight = 36.dp
private val UsagePresetMinutes = listOf(5, 15, 30, 60, 120)

internal fun formatUsageDuration(millis: Long): String {
    if (millis <= 0L) return "0m"
    val totalMinutes = millis / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        minutes > 0L -> "${minutes}m"
        else -> "<1m"
    }
}

@Composable
private fun InstalledAppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    val icon = bitmap
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = packageName.firstOrNull()?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun TimerDialog(
    app: AppRuleUiModel,
    onDismiss: () -> Unit,
    onSave: (waitSeconds: Int, usageMinutes: Int) -> Unit,
) {
    var waitSeconds by remember(app.packageName) { mutableIntStateOf(app.waitTimeSeconds) }
    var usageMinutes by remember(app.packageName) { mutableIntStateOf(app.usageLimitMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            InstalledAppIcon(
                packageName = app.packageName,
                modifier = Modifier.size(48.dp),
            )
        },
        title = {
            Text(
                text = stringResource(R.string.timer_dialog_title, app.label),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimerSettingCard(
                    icon = Icons.Outlined.HourglassEmpty,
                    label = stringResource(R.string.timer_wait_label),
                ) {
                    WaitSecondsWheel(
                        initialSeconds = waitSeconds,
                        onSecondsChange = { waitSeconds = it },
                    )
                }
                TimerSettingCard(
                    icon = Icons.Outlined.Timer,
                    label = stringResource(R.string.timer_usage_label),
                ) {
                    UsagePresetChips(
                        selectedMinutes = usageMinutes,
                        onMinutesChange = { usageMinutes = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(waitSeconds, usageMinutes)
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun TimerSettingCard(
    icon: ImageVector,
    label: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun WaitSecondsWheel(
    initialSeconds: Int,
    onSecondsChange: (Int) -> Unit,
) {
    val values = remember { (0..RuleConstraints.MaxWaitSeconds step WaitWheelStepSeconds).toList() }
    val initialIndex = remember {
        values.indices.minByOrNull { abs(values[it] - initialSeconds) } ?: 0
    }
    // Spacer rows above and below let the first and last values reach the
    // center slot, so list index = value index + WheelPadRows.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val centeredIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val centeredListIndex = info.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2 - center) }
                ?.index
                ?: (initialIndex + WheelPadRows)
            (centeredListIndex - WheelPadRows).coerceIn(values.indices)
        }
    }

    LaunchedEffect(centeredIndex) { onSecondsChange(values[centeredIndex]) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WheelItemHeight * WheelVisibleRows),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(WheelItemHeight),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {}
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(WheelPadRows) { Spacer(Modifier.height(WheelItemHeight)) }
            itemsIndexed(values) { index, seconds ->
                val distance = abs(index - centeredIndex)
                Box(
                    modifier = Modifier.height(WheelItemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.duration_seconds_short, seconds),
                        style = if (distance == 0) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        fontWeight = if (distance == 0) FontWeight.SemiBold else FontWeight.Normal,
                        color = when (distance) {
                            0 -> MaterialTheme.colorScheme.onSecondaryContainer
                            1 -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    )
                }
            }
            items(WheelPadRows) { Spacer(Modifier.height(WheelItemHeight)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsagePresetChips(
    selectedMinutes: Int,
    onMinutesChange: (Int) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UsagePresetMinutes.forEach { minutes ->
            FilterChip(
                selected = selectedMinutes == minutes,
                onClick = { onMinutesChange(minutes) },
                shape = CircleShape,
                label = {
                    Text(
                        text = if (minutes % 60 == 0) {
                            stringResource(R.string.duration_hours_short, minutes / 60)
                        } else {
                            stringResource(R.string.duration_minutes_short, minutes)
                        },
                    )
                },
            )
        }
    }
}
