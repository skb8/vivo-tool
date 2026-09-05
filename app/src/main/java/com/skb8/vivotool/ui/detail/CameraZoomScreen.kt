package com.skb8.vivotool.ui.detail

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skb8.vivotool.R
import com.skb8.vivotool.hooks.camera.CameraFeatureConfigHook
import com.skb8.vivotool.hooks.camera.CameraZoomHook
import com.skb8.vivotool.settings.AppSettings
import com.skb8.vivotool.ui.AppControl
import kotlinx.coroutines.launch
import java.util.Locale

/** Готовые значения, чтобы не набирать руками. */
private val PRESETS = listOf(20f, 50f, 100f, 120f, 200f)

/** Настройки твика «Максимальный зум»: значение предела и подсказки. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraZoomScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings(context) }

    var saved by remember {
        mutableFloatStateOf(
            CameraZoomHook.clamp(
                settings.getFloat(CameraZoomHook.MAX_ZOOM_KEY, CameraZoomHook.DEFAULT_MAX_ZOOM)
            )
        )
    }
    var input by remember { mutableStateOf(formatZoom(saved)) }

    val parsed = input.trim().replace(',', '.').toFloatOrNull()
    val valid = parsed != null && parsed >= CameraZoomHook.MIN_MAX_ZOOM &&
        parsed <= CameraZoomHook.MAX_MAX_ZOOM

    fun save(value: Float) {
        val clamped = CameraZoomHook.clamp(value)
        settings.setFloat(CameraZoomHook.MAX_ZOOM_KEY, clamped)
        saved = clamped
        input = formatZoom(clamped)
        Toast.makeText(context, R.string.camera_zoom_saved, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.camera_zoom_screen_title)) },
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
                                AppControl.stopAndReport(
                                    context,
                                    CameraFeatureConfigHook.CAMERA_PACKAGE
                                )
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
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.camera_zoom_current, formatZoom(saved)),
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = !valid,
                label = { Text(stringResource(R.string.camera_zoom_field_label)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (valid) R.string.camera_zoom_field_hint else R.string.camera_zoom_invalid,
                            formatZoom(CameraZoomHook.MIN_MAX_ZOOM),
                            formatZoom(CameraZoomHook.MAX_MAX_ZOOM)
                        )
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PRESETS.forEach { preset ->
                    FilterChip(
                        selected = saved == preset,
                        onClick = { save(preset) },
                        label = {
                            Text(stringResource(R.string.camera_zoom_preset, formatZoom(preset)))
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { parsed?.let(::save) },
                    enabled = valid && parsed != saved
                ) {
                    Text(stringResource(R.string.action_apply))
                }
                if (saved != CameraZoomHook.DEFAULT_MAX_ZOOM) {
                    TextButton(onClick = { save(CameraZoomHook.DEFAULT_MAX_ZOOM) }) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(
                                R.string.camera_zoom_default,
                                formatZoom(CameraZoomHook.DEFAULT_MAX_ZOOM)
                            )
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.how_it_works),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.camera_zoom_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 120.0 → «120», 12.5 → «12.5». */
private fun formatZoom(value: Float): String =
    if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
