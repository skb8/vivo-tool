package com.skb8.vivotool.ui.detail

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skb8.vivotool.hooks.framework.FreeformWindowLimitHook
import com.skb8.vivotool.settings.AppSettings

/** Настройки твика «Больше плавающих окон». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeformLimitScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var mode by remember {
        mutableIntStateOf(
            settings.getInt(FreeformWindowLimitHook.KEY_MODE, FreeformWindowLimitHook.DEFAULT_MODE)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Плавающие окна") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Сколько плавающих окон может оставаться на экране",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            ModeOption(
                title = "Без ограничения",
                subtitle = "Прошивка не сворачивает окна принудительно. " +
                    "Общий лимит списка задач (12) остаётся.",
                selected = mode == FreeformWindowLimitHook.MODE_UNLIMITED,
                onSelect = {
                    mode = FreeformWindowLimitHook.MODE_UNLIMITED
                    settings.setInt(
                        FreeformWindowLimitHook.KEY_MODE,
                        FreeformWindowLimitHook.MODE_UNLIMITED
                    )
                }
            )

            ModeOption(
                title = "Два окна",
                subtitle = "Лимит самой прошивки для устройств с поддержкой " +
                    "нескольких видимых окон.",
                selected = mode == FreeformWindowLimitHook.MODE_TWO,
                onSelect = {
                    mode = FreeformWindowLimitHook.MODE_TWO
                    settings.setInt(
                        FreeformWindowLimitHook.KEY_MODE,
                        FreeformWindowLimitHook.MODE_TWO
                    )
                }
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Как это работает",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Прошивка считает лимит видимых окон как " +
                            "«1, если notSupportMultiVisibleFreeform, иначе 2», и всё лишнее " +
                            "сворачивает в мини-окно. Твик всегда сообщает о поддержке " +
                            "нескольких окон, а в режиме «без ограничения» ещё и отключает " +
                            "принудительное сворачивание — и для обычных, и для игровых окон.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "com.android.server.wm.VivoFreeformWindowManager",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Режим применяется сразу, но сам твик включается при запуске " +
                            "system_server — после включения переключателя нужна перезагрузка.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Если не работает",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "1. В LSPosed в области действия модуля должен быть отмечен " +
                            "«Системный фреймворк» (android).\n" +
                            "2. Перезагрузить устройство.\n" +
                            "3. Открыть в LSPosed журнал модуля и найти строки с тегом " +
                            "VivoTool: там видно, какие классы и методы нашлись в прошивке " +
                            "и кто именно сворачивает окна.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
