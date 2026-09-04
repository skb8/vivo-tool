package com.skb8.vivotool.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skb8.vivotool.BuildConfig
import com.skb8.vivotool.R
import com.skb8.vivotool.core.HookRegistry
import com.skb8.vivotool.core.ModuleScope
import com.skb8.vivotool.core.ModuleStatus
import com.skb8.vivotool.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Главный экран: статус модуля и список приложений, для которых есть твики. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(onOpenApp: (TargetApp) -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val scopePackages = remember { ModuleScope.packages(context) }
    val missingScope = remember { HookRegistry.packagesMissingFromScope(scopePackages) }
    val apps by produceState(initialValue = emptyList<TargetApp>()) {
        value = withContext(Dispatchers.IO) { TargetApps.load(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { StatusCard() }

            if (!settings.isSharedWithHooks) {
                item {
                    NoticeCard(
                        icon = Icons.Rounded.WarningAmber,
                        title = stringResource(R.string.prefs_unavailable_title),
                        text = stringResource(R.string.prefs_unavailable_text)
                    )
                }
            }

            if (missingScope.isNotEmpty()) {
                item {
                    NoticeCard(
                        icon = Icons.Rounded.ErrorOutline,
                        title = stringResource(R.string.scope_missing_title),
                        text = stringResource(
                            R.string.scope_missing_text,
                            missingScope.joinToString("\n") { "• $it" }
                        )
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.apps_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            if (HookRegistry.hooks.isEmpty()) {
                item {
                    NoticeCard(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.no_tweaks_title),
                        text = stringResource(R.string.no_tweaks_text)
                    )
                }
            } else if (apps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(apps, key = { it.packageName }) { app ->
                    AppRow(app = app, onClick = { onOpenApp(app) })
                }
            }

            item { Footer() }
        }
    }
}

@Composable
private fun AppRow(app: TargetApp, onClick: () -> Unit) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                val tweaks = pluralStringResource(
                    R.plurals.tweak_count,
                    app.hooks.size,
                    app.hooks.size
                )
                Text(
                    text = if (app.installed) {
                        tweaks
                    } else {
                        stringResource(R.string.app_not_installed, tweaks)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun StatusCard() {
    val active = ModuleStatus.isActive()
    val container =
        if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.errorContainer
    val content =
        if (active) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onErrorContainer

    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (active) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(
                        if (active) R.string.module_active else R.string.module_inactive
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = content
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (active) {
                        stringResource(
                            R.string.module_active_details,
                            ModuleStatus.frameworkName(),
                            ModuleStatus.xposedApiVersion()
                        )
                    } else {
                        stringResource(R.string.module_inactive_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun NoticeCard(icon: ImageVector, title: String, text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(Modifier.padding(20.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun Footer() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(
                R.string.footer_summary,
                BuildConfig.VERSION_NAME,
                HookRegistry.hooks.size
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
