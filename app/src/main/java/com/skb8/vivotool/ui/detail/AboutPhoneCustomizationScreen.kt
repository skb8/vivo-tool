package com.skb8.vivotool.ui.detail

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.skb8.vivotool.R
import com.skb8.vivotool.core.AboutPhoneConfig
import com.skb8.vivotool.core.AboutPhoneRow
import com.skb8.vivotool.core.AboutPhoneVisibility
import com.skb8.vivotool.settings.AppSettings
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPhoneCustomizationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var config by remember { mutableStateOf(AboutPhoneConfig.decode(settings.getString(AboutPhoneConfig.PREF_KEY))) }
    var editing by remember { mutableStateOf<AboutPhoneRow?>(null) }
    var resetAll by remember { mutableStateOf(false) }
    var overrideModel by remember { mutableStateOf(config.modelName != null) }
    var model by remember { mutableStateOf(config.modelName.orEmpty()) }
    var hideModel by remember { mutableStateOf(config.hideModel) }

    fun save(value: AboutPhoneConfig) {
        settings.setString(AboutPhoneConfig.PREF_KEY, value.encode())
        config = value
    }

    fun move(row: AboutPhoneRow, direction: Int) {
        val custom = config.rows.filter { it.key.startsWith(AboutPhoneConfig.CUSTOM_PREFIX) }.toMutableList()
        val from = custom.indexOfFirst { it.key == row.key }
        val to = from + direction
        if (from >= 0 && to in custom.indices) {
            custom.add(to, custom.removeAt(from))
            save(config.copy(rows = config.rows.filterNot { it.key.startsWith(AboutPhoneConfig.CUSTOM_PREFIX) } + custom))
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.hook_about_customization_title)) },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
            } },
            actions = { IconButton(onClick = { resetAll = true }) {
                Icon(Icons.Rounded.RestartAlt, stringResource(R.string.action_reset_all))
            } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text(stringResource(R.string.about_customization_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.about_model_label), style = MaterialTheme.typography.titleSmall)
                        CheckOption(stringResource(R.string.about_model_override), overrideModel) { overrideModel = it }
                        if (overrideModel) OutlinedTextField(value = model, onValueChange = { model = it.take(2000) },
                            label = { Text(stringResource(R.string.about_model_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.about_model_hide), Modifier.weight(1f))
                            Switch(hideModel, { hideModel = it })
                        }
                        Button(onClick = {
                            save(config.copy(modelName = if (overrideModel) model else null, hideModel = hideModel))
                            Toast.makeText(context, R.string.about_customization_saved, Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.action_apply)) }
                    }
                }
            }
            item { Text(stringResource(R.string.about_builtin_rows), style = MaterialTheme.typography.titleMedium) }
            items(AboutPhoneConfig.fields, key = { it.key }) { field ->
                val row = config.row(field.key) ?: AboutPhoneRow(field.key)
                Card {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { editing = row }.padding(16.dp)) {
                            Text(stringResource(field.labelRes), style = MaterialTheme.typography.titleSmall)
                            Text(visibilityLabel(row.visibility), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (row.title != null || row.value != null) {
                                Text(listOfNotNull(row.title, row.value).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (config.row(field.key) != null) IconButton(onClick = { save(config.withoutRow(field.key)) }) {
                            Icon(Icons.Rounded.RestartAlt, stringResource(R.string.action_reset))
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.about_custom_rows), style = MaterialTheme.typography.titleMedium) }
            val customRows = config.rows.filter { it.key.startsWith(AboutPhoneConfig.CUSTOM_PREFIX) }
            items(customRows, key = { it.key }) { row ->
                Card {
                    Column(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().clickable { editing = row }.padding(16.dp)) {
                            Text(row.title.orEmpty(), style = MaterialTheme.typography.titleSmall)
                            Text(row.value.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            Text(visibilityLabel(row.visibility), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            IconButton(onClick = { move(row, -1) }, enabled = row != customRows.firstOrNull()) {
                                Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.about_move_up))
                            }
                            IconButton(onClick = { move(row, 1) }, enabled = row != customRows.lastOrNull()) {
                                Icon(Icons.Rounded.ArrowDownward, stringResource(R.string.about_move_down))
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { save(config.withoutRow(row.key)) }) {
                                Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.about_delete_row))
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = { editing = AboutPhoneRow(AboutPhoneConfig.CUSTOM_PREFIX + UUID.randomUUID(),
                    "", "", AboutPhoneVisibility.SHOW) }, enabled = customRows.size < AboutPhoneConfig.MAX_CUSTOM_ROWS,
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.about_add_row), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    editing?.let { row ->
        AboutPhoneRowDialog(row, onDismiss = { editing = null }, onSave = {
            save(config.withRow(it))
            editing = null
        })
    }
    if (resetAll) AlertDialog(onDismissRequest = { resetAll = false },
        title = { Text(stringResource(R.string.about_reset_title)) },
        text = { Text(stringResource(R.string.about_reset_text)) },
        confirmButton = { TextButton(onClick = {
            settings.remove(AboutPhoneConfig.PREF_KEY)
            config = AboutPhoneConfig()
            overrideModel = false
            model = ""
            hideModel = false
            resetAll = false
        }) { Text(stringResource(R.string.action_reset_all)) } },
        dismissButton = { TextButton(onClick = { resetAll = false }) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
private fun AboutPhoneRowDialog(row: AboutPhoneRow, onDismiss: () -> Unit, onSave: (AboutPhoneRow) -> Unit) {
    val custom = row.key.startsWith(AboutPhoneConfig.CUSTOM_PREFIX)
    var title by remember(row.key) { mutableStateOf(row.title.orEmpty()) }
    var value by remember(row.key) { mutableStateOf(row.value.orEmpty()) }
    var overrideTitle by remember(row.key) { mutableStateOf(custom || row.title != null) }
    var overrideValue by remember(row.key) { mutableStateOf(custom || row.value != null) }
    var visibility by remember(row.key) { mutableStateOf(row.visibility) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_edit_row)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!custom) CheckOption(stringResource(R.string.about_override_title), overrideTitle) { overrideTitle = it }
                if (overrideTitle) OutlinedTextField(title, { title = it.take(2000) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.about_row_title)) }, singleLine = true)
                if (!custom) CheckOption(stringResource(R.string.about_override_value), overrideValue) { overrideValue = it }
                if (overrideValue) OutlinedTextField(value, { value = it.take(2000) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.about_row_value)) }, maxLines = 4)
                Text(stringResource(R.string.about_visibility), style = MaterialTheme.typography.titleSmall)
                AboutPhoneVisibility.entries.filter { !custom || it != AboutPhoneVisibility.DEFAULT }.forEach { mode ->
                    Row(Modifier.fillMaxWidth().clickable { visibility = mode }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(visibility == mode, onClick = { visibility = mode })
                        Text(visibilityLabel(mode))
                    }
                }
                if (!custom) Text(stringResource(R.string.about_show_missing_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(row.copy(title = if (overrideTitle) title else null,
            value = if (overrideValue) value else null, visibility = visibility)) },
            enabled = !custom || title.isNotBlank()) { Text(stringResource(R.string.action_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun CheckOption(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
private fun visibilityLabel(visibility: AboutPhoneVisibility): String = stringResource(when (visibility) {
    AboutPhoneVisibility.DEFAULT -> R.string.about_visibility_default
    AboutPhoneVisibility.SHOW -> R.string.about_visibility_show
    AboutPhoneVisibility.HIDE -> R.string.about_visibility_hide
})
