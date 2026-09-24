package com.skb8.vivotool.ui.detail

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.skb8.vivotool.R
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.hooks.player.VivoIslandAppsHook
import com.skb8.vivotool.settings.AppSettings
import com.skb8.vivotool.ui.AppControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isDefaultSupported: Boolean
)

private val INSTALLED_APPS_PERMISSIONS = arrayOf(
    "com.android.permission.GET_INSTALLED_APPS",
    "android.permission.GET_INSTALLED_APPS",
    "com.vivo.permission.GET_INSTALLED_APPS"
)

private fun hasInstalledAppsPermission(context: Context): Boolean =
    INSTALLED_APPS_PERMISSIONS.any { perm ->
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

/**
 * Экран выбора приложений для показа в динамическом острове OriginOS (Origin Player).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OriginPlayerIslandAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings(context) }
    var selectedApps by remember { mutableStateOf(settings.getIslandApps()) }
    var searchQuery by remember { mutableStateOf("") }
    var reloadTrigger by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            reloadTrigger++
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasInstalledAppsPermission(context)) {
            permissionLauncher.launch(INSTALLED_APPS_PERMISSIONS)
        }
    }

    val installedAppsState = produceState(
        initialValue = emptyList<AppEntry>() to true,
        key1 = reloadTrigger
    ) {
        val apps = withContext(Dispatchers.IO) {
            loadInstalledApps(context)
        }
        value = apps to false
    }

    val (installedApps, isLoading) = installedAppsState.value

    val filteredApps = remember(installedApps, searchQuery, selectedApps) {
        val query = searchQuery.trim().lowercase()
        installedApps
            .filter { app ->
                query.isEmpty() ||
                    app.label.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query)
            }
            .sortedWith(
                compareByDescending<AppEntry> { it.packageName in selectedApps }
                    .thenBy { it.label.lowercase() }
            )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hook_player_island_apps_title)) },
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
                            scope.launch {
                                AppControl.stopAndReport(context, Constants.ORIGIN_PLAYER_PACKAGE)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.StopCircle,
                            contentDescription = stringResource(R.string.action_force_stop)
                        )
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
            // Верхняя плашка с информацией о количестве приложений по умолчанию
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(
                                    R.string.island_apps_default_count,
                                    VivoIslandAppsHook.DEFAULT_ISLAND_PACKAGES.size
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selectedApps.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(
                                        R.string.island_apps_selected_count,
                                        selectedApps.size
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Поле поиска
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.island_apps_search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.island_apps_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (installedApps.isEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    permissionLauncher.launch(INSTALLED_APPS_PERMISSIONS)
                                },
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text(stringResource(R.string.island_apps_grant_permission))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isChecked = app.packageName in selectedApps

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked) {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newSet = selectedApps.toMutableSet()
                                    if (isChecked) {
                                        newSet.remove(app.packageName)
                                    } else {
                                        newSet.add(app.packageName)
                                    }
                                    selectedApps = newSet
                                    settings.setIslandApps(newSet)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    ) {}
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (app.isDefaultSupported) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.island_apps_built_in_badge),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(Modifier.width(8.dp))

                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        val newSet = selectedApps.toMutableSet()
                                        if (checked) {
                                            newSet.add(app.packageName)
                                        } else {
                                            newSet.remove(app.packageName)
                                        }
                                        selectedApps = newSet
                                        settings.setIslandApps(newSet)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadInstalledApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    return try {
        val appList: List<ApplicationInfo> = try {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            if (apps.isNotEmpty()) apps else {
                pm.getInstalledPackages(0).mapNotNull { it.applicationInfo }
            }
        } catch (_: Throwable) {
            try {
                pm.getInstalledPackages(0).mapNotNull { it.applicationInfo }
            } catch (_: Throwable) {
                emptyList()
            }
        }

        appList
            .filter { it.packageName != context.packageName }
            .map { appInfo ->
                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Throwable) {
                    appInfo.packageName
                }
                val icon = try {
                    pm.getApplicationIcon(appInfo).toBitmap(128, 128).asImageBitmap()
                } catch (_: Throwable) {
                    null
                }
                AppEntry(
                    packageName = appInfo.packageName,
                    label = label,
                    icon = icon,
                    isDefaultSupported = appInfo.packageName in VivoIslandAppsHook.DEFAULT_ISLAND_PACKAGES
                )
            }
            .sortedBy { it.label.lowercase() }
    } catch (_: Throwable) {
        emptyList()
    }
}
