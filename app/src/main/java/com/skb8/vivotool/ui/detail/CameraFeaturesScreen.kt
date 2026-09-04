package com.skb8.vivotool.ui.detail

import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skb8.vivotool.R
import com.skb8.vivotool.hooks.camera.CameraFeatureConfigHook
import com.skb8.vivotool.settings.AppSettings
import com.skb8.vivotool.settings.CameraFeature
import com.skb8.vivotool.settings.CameraFeatureCatalog
import com.skb8.vivotool.settings.CameraFeatureLoader
import com.skb8.vivotool.ui.AppControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Настройки твика «Изменение доступных фич»: поиск по списку фич камеры,
 * значение из прошивки под каждой и выбор из трёх состояний.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraFeaturesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings(context) }
    val overrides = remember {
        mutableStateMapOf<String, Boolean>().apply {
            settings.booleanEntriesWithPrefix(CameraFeatureConfigHook.KEY_PREFIX)
                .forEach { (key, value) -> put(CameraFeatureConfigHook.featureName(key), value) }
        }
    }
    var query by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }

    val catalog by produceState<CameraFeatureCatalog?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { CameraFeatureLoader.load(context) }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_features_title)) },
            text = { Text(stringResource(R.string.reset_features_text, overrides.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        settings.removeWithPrefix(CameraFeatureConfigHook.KEY_PREFIX)
                        overrides.clear()
                        confirmReset = false
                    }
                ) {
                    Text(stringResource(R.string.action_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.camera_features_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch { forceStopCamera(context) }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.StopCircle,
                            contentDescription = stringResource(R.string.action_force_stop)
                        )
                    }
                    if (overrides.isNotEmpty()) {
                        IconButton(onClick = { confirmReset = true }) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = stringResource(R.string.action_reset_all)
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
                placeholder = { Text(stringResource(R.string.camera_features_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.action_clear)
                            )
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
                                    text = stringResource(R.string.camera_features_empty),
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

private suspend fun forceStopCamera(context: Context) {
    val packageName = CameraFeatureConfigHook.CAMERA_PACKAGE
    val stopped = withContext(Dispatchers.IO) { AppControl.forceStop(packageName) }
    if (stopped) {
        Toast.makeText(
            context,
            context.getString(R.string.force_stop_done, packageName),
            Toast.LENGTH_SHORT
        ).show()
    } else {
        Toast.makeText(context, R.string.force_stop_failed, Toast.LENGTH_LONG).show()
        AppControl.openAppInfo(context, packageName)
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
                text = if (changed > 0) {
                    stringResource(
                        R.string.camera_features_summary_changed,
                        catalog.features.size,
                        changed
                    )
                } else {
                    stringResource(R.string.camera_features_summary, catalog.features.size)
                },
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
                text = stringResource(R.string.camera_features_hint),
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
                    text = stringResource(R.string.camera_features_error_title),
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
                    text = stringResource(R.string.camera_features_product, catalog.product),
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
                        text = stringResource(R.string.feature_override, override.toString()),
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
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.feature_value_menu)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuChoice(stringResource(R.string.feature_value_default), override == null) {
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

@Composable
private fun defaultText(feature: CameraFeature): String = when {
    feature.parameterized && feature.overloads > 1 ->
        stringResource(R.string.feature_default_args_overloads, feature.overloads)

    feature.parameterized -> stringResource(R.string.feature_default_args)
    feature.defaultValue != null ->
        stringResource(R.string.feature_default_value, feature.defaultValue.toString())

    else -> stringResource(R.string.feature_default_unknown)
}
