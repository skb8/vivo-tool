package com.skb8.vivotool.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skb8.vivotool.BuildConfig
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.HookRegistry
import com.skb8.vivotool.core.ModuleScope
import com.skb8.vivotool.core.ModuleStatus
import com.skb8.vivotool.hooks.settings.AboutPhoneRomImageHook
import com.skb8.vivotool.settings.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    imageVersion: Int,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val categories = remember { HookRegistry.byCategory() }
    val enabledState = remember {
        mutableStateMapOf<String, Boolean>().apply {
            HookRegistry.hooks.forEach { put(it.id, settings.isEnabled(it)) }
        }
    }
    val scopePackages = remember { ModuleScope.packages(context) }
    val missingScope = remember { HookRegistry.packagesMissingFromScope(scopePackages) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vivo Tool") },
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
                        title = "Настройки недоступны хукам",
                        text = "Не удалось открыть файл настроек в режиме world-readable. " +
                            "Активируйте модуль в LSPosed и перезапустите приложение."
                    )
                }
            }

            if (missingScope.isNotEmpty()) {
                item {
                    NoticeCard(
                        icon = Icons.Rounded.ErrorOutline,
                        title = "Пакеты вне области действия",
                        text = "Для этих приложений есть хуки, но они не перечислены в " +
                            "app/module-scope.txt, поэтому LSPosed не предложит их при выборе:\n" +
                            missingScope.joinToString("\n") { "• $it" }
                    )
                }
            }

            item { ScopeCard(scopePackages) }

            if (HookRegistry.hooks.isEmpty()) {
                item { EmptyHooksCard() }
            } else {
                categories.forEach { (category, hooks) ->
                    item {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                    }
                    items(hooks, key = { it.id }) { hook ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HookRow(
                                hook = hook,
                                enabled = enabledState[hook.id] ?: hook.enabledByDefault,
                                onToggle = { value ->
                                    enabledState[hook.id] = value
                                    settings.setEnabled(hook, value)
                                }
                            )
                            if (hook.id == AboutPhoneRomImageHook.ID) {
                                AboutPhoneImageCard(
                                    imageVersion = imageVersion,
                                    onPickImage = onPickImage,
                                    onClearImage = onClearImage
                                )
                            }
                        }
                    }
                }
            }

            item { FooterCard() }
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
                    text = if (active) "Модуль активен" else "Модуль не активирован",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = content
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (active) {
                        "${ModuleStatus.frameworkName()} · Xposed API v${ModuleStatus.xposedApiVersion()}"
                    } else {
                        "Включите модуль в LSPosed и перезагрузите устройство"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ScopeCard(packages: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Extension, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Область действия",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (packages.isEmpty()) {
                    "Список пуст — LSPosed предложит выбрать любое приложение. " +
                        "Добавьте нужные пакеты в app/module-scope.txt."
                } else {
                    "LSPosed предложит выбрать ${packages.size} приложений:"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (packages.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = packages.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun EmptyHooksCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Code, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Хуков пока нет",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Каркас готов к работе. Чтобы добавить хук:\n" +
                    "1. Скопируйте hooks/template/TemplateHook.kt\n" +
                    "2. Зарегистрируйте его в hooks/HookModules.kt\n" +
                    "3. Добавьте пакет приложения в app/module-scope.txt\n\n" +
                    "Подробная инструкция — docs/WRITING_HOOKS.md",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HookRow(
    hook: BaseHook,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = hook.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (hook.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = hook.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = hook.targetPackages.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun NoticeCard(icon: ImageVector, title: String, text: String) {
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
private fun FooterCard() {
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
            text = "v${BuildConfig.VERSION_NAME} · хуков: ${HookRegistry.hooks.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
