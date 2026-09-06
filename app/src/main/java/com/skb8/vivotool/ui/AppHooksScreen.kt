package com.skb8.vivotool.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.settings.AppSettings
import com.skb8.vivotool.settings.RequiredFeatures
import kotlinx.coroutines.launch

/**
 * Твики одного приложения.
 *
 * Тап по переключателю включает твик, тап по названию открывает его настройки,
 * если они есть.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHooksScreen(
    app: TargetApp,
    onBack: () -> Unit,
    onOpenHook: (BaseHook) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings(context) }
    val enabledState = remember(app.packageName) {
        mutableStateMapOf<String, Boolean>().apply {
            app.hooks.forEach { put(it.id, settings.isEnabled(it)) }
        }
    }
    // Останавливать можно только реальное приложение: у системного фреймворка
    // и универсального «*» force stop смысла не имеет.
    val canForceStop = app.installed &&
        app.packageName != Constants.SYSTEM_FRAMEWORK &&
        app.packageName != Constants.ALL_PACKAGES

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (canForceStop) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    AppControl.stopAndReport(context, app.packageName)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.StopCircle,
                                contentDescription = stringResource(R.string.action_force_stop)
                            )
                        }
                    }
                },
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
            if (app.packageName == Constants.SYSTEM_FRAMEWORK) {
                item {
                    NoticeCard(
                        icon = Icons.Rounded.RestartAlt,
                        title = stringResource(R.string.reboot_needed_title),
                        text = stringResource(R.string.reboot_needed_text)
                    )
                }
            }

            items(app.hooks, key = { it.id }) { hook ->
                HookRow(
                    hook = hook,
                    enabled = enabledState[hook.id] ?: hook.enabledByDefault,
                    hasDetails = HookDetails.hasDetails(hook.id),
                    onToggle = { value ->
                        enabledState[hook.id] = value
                        settings.setEnabled(hook, value)
                        RequiredFeatures.apply(settings, hook, value)
                    },
                    onOpenDetails = { onOpenHook(hook) }
                )
            }
        }
    }
}

@Composable
private fun HookRow(
    hook: BaseHook,
    enabled: Boolean,
    hasDetails: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenDetails: () -> Unit
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { if (hasDetails) onOpenDetails() else onToggle(!enabled) }
                    .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(hook.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (hook.descriptionRes != 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(hook.descriptionRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hasDetails) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.tap_to_configure),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (hasDetails) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            VerticalDivider(modifier = Modifier.height(36.dp))
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
    }
}
