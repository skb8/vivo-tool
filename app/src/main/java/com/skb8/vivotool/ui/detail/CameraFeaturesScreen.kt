package com.skb8.vivotool.ui.detail

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skb8.vivotool.hooks.camera.CameraFeatureConfigHook
import com.skb8.vivotool.settings.AppSettings
import com.skb8.vivotool.settings.CameraFeature
import com.skb8.vivotool.settings.CameraFeatureCatalog
import com.skb8.vivotool.settings.CameraFeatureLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Настройки твика «Изменение доступных фич»: поиск по списку фич камеры,
 * значение из прошивки под каждой и выбор из трёх состояний.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraFeaturesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val overrides = remember {
        mutableStateMapOf<String, Boolean>().apply {
            settings.booleanEntriesWithPrefix(CameraFeatureConfigHook.KEY_PREFIX)
                .forEach { (key, value) -> put(CameraFeatureConfigHook.featureName(key), value) }
        }
    }
    var query by remember { mutableStateOf("") }

    val catalog by produceState<CameraFeatureCatalog?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { CameraFeatureLoader.load(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Фичи камеры") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (overrides.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                settings.removeWithPrefix(CameraFeatureConfigHook.KEY_PREFIX)
                                overrides.clear()
                            }
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = "Сбросить всё")
                        }
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
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Поиск фичи") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Очистить")
                        }
                    }
                }
            )

            val loaded = catalog
            when {
                loaded == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                loaded.error != null -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    ErrorCard(loaded)
                }

                else -> {
                    val visible = remember(loaded, query) {
                        if (query.isBlank()) {
                            loaded.features
                        } else {
                            loaded.features.filter { it.name.contains(query.trim(), true) }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { CatalogHeader(loaded, overrides.size) }

                        if (visible.isEmpty()) {
                            item {
                                Text(
                                    text = "Ничего не найдено",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            }
                        }

                        items(visible, key = { it.name }) { feature ->
                            FeatureRow(
                                feature = feature,
                                override = overrides[feature.name],
                                onSelect = { value ->
                                    val key = CameraFeatureConfigHook.settingsKey(feature.name)
                                    if (value == null) {
                                        settings.remove(key)
                                        overrides.remove(feature.name)
                                    } else {
                                        settings.setBoolean(key, value)
                                        overrides[feature.name] = value
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogHeader(catalog: CameraFeatureCatalog, changed: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Фич найдено: ${catalog.features.size}" +
                    if (changed > 0) " · изменено: $changed" else "",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = catalog.configClassName.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Значения читаются из прошивки. После изменения закройте камеру " +
                    "(force stop) — переопределения применяются при её запуске.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorCard(catalog: CameraFeatureCatalog) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Не удалось прочитать конфигурацию",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = catalog.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "ro.product.name = ${catalog.product}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    feature: CameraFeature,
    override: Boolean?,
    onSelect: (Boolean?) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = feature.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = defaultText(feature),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (override != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "переопределено: $override",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = feature.declaredIn,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline
            )

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Значение фичи")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuChoice("По умолчанию", override == null) {
                        onSelect(null)
                        menuOpen = false
                    }
                    MenuChoice("true", override == true) {
                        onSelect(true)
                        menuOpen = false
                    }
                    MenuChoice("false", override == false) {
                        onSelect(false)
                        menuOpen = false
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    )
}

private fun defaultText(feature: CameraFeature): String = when {
    feature.parameterized ->
        "по умолчанию: зависит от аргументов" +
            if (feature.overloads > 1) " · перегрузок: ${feature.overloads}" else ""

    feature.defaultValue != null -> "по умолчанию: ${feature.defaultValue}"
    else -> "по умолчанию: не удалось определить"
}
