package com.skb8.vivotool.ui.detail

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skb8.vivotool.R
import com.skb8.vivotool.core.AboutPhoneConfig
import com.skb8.vivotool.core.AboutPhoneRow
import com.skb8.vivotool.core.AboutPhoneVisibility
import com.skb8.vivotool.hooks.settings.AboutPhoneCustomizationHook
import com.skb8.vivotool.settings.AppSettings
import com.skb8.vivotool.ui.AppControl
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPhoneCustomizationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var config by remember { mutableStateOf(AboutPhoneConfig.decode(settings.getString(AboutPhoneConfig.PREF_KEY))) }
    var enabled by remember { mutableStateOf(settings.isEnabled(AboutPhoneCustomizationHook)) }
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editingModel by rememberSaveable { mutableStateOf(false) }
    var resetAll by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var stopping by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var notificationJob by remember { mutableStateOf<Job?>(null) }

    fun save(value: AboutPhoneConfig, messageRes: Int, undo: (() -> Unit)? = null): Boolean {
        if (!runCatching { settings.setString(AboutPhoneConfig.PREF_KEY, value.encode()) }.getOrDefault(false)) {
            Toast.makeText(context, R.string.about_save_failed, Toast.LENGTH_LONG).show()
            return false
        }
        config = value
        notificationJob?.cancel()
        snackbar.currentSnackbarData?.dismiss()
        notificationJob = scope.launch {
            val result = snackbar.showSnackbar(context.getString(messageRes),
                actionLabel = if (undo != null) context.getString(R.string.about_undo) else null,
                withDismissAction = undo != null)
            if (result == SnackbarResult.ActionPerformed) undo?.invoke()
        }
        return true
    }

    if (editingModel) {
        AboutPhoneModelEditor(config, onBack = { editingModel = false }, onSave = { name, hide ->
            if (save(config.copy(modelName = name, hideModel = hide), R.string.about_name_saved)) editingModel = false
        })
        return
    }
    editingKey?.let { key ->
        val isNew = config.row(key) == null && key.startsWith(AboutPhoneConfig.CUSTOM_PREFIX)
        val row = config.row(key) ?: AboutPhoneRow(key, title = if (isNew) "" else null,
            value = if (isNew) "" else null,
            visibility = if (isNew) AboutPhoneVisibility.SHOW else AboutPhoneVisibility.DEFAULT)
        val label = AboutPhoneConfig.fields.firstOrNull { it.key == key }?.let { context.getString(it.labelRes) }
        AboutPhoneRowEditor(row, label, isNew, onBack = { editingKey = null }, onSave = {
            val original = !key.startsWith(AboutPhoneConfig.CUSTOM_PREFIX) && it == AboutPhoneRow(key)
            if (save(if (original) config.withoutRow(key) else config.withRow(it), R.string.about_entry_saved)) {
                if (isNew) query = ""
                editingKey = null
            }
        }, onRemove = {
            val previous = config
            val saved = save(config.withoutRow(key), if (label == null) R.string.about_entry_deleted else R.string.about_entry_reset) {
                save(previous, R.string.about_action_undone)
            }
            if (saved) editingKey = null
        })
        return
    }

    val customRows = config.rows.filter { it.key.startsWith(AboutPhoneConfig.CUSTOM_PREFIX) }
    fun move(row: AboutPhoneRow, direction: Int) {
        val reordered = customRows.toMutableList()
        val from = reordered.indexOfFirst { it.key == row.key }
        val to = from + direction
        if (from >= 0 && to in reordered.indices) {
            reordered.add(to, reordered.removeAt(from))
            save(config.copy(rows = config.rows.filterNot { it.key.startsWith(AboutPhoneConfig.CUSTOM_PREFIX) } + reordered),
                R.string.about_order_saved)
        }
    }

    Scaffold(
        topBar = { EditorTopBar(stringResource(R.string.hook_about_customization_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(modifier = Modifier.navigationBarsPadding(), tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(stringResource(R.string.about_restart_hint), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        stopping = true
                        scope.launch {
                            try { AppControl.stopAndReport(context, "com.android.settings") }
                            finally { stopping = false }
                        }
                    }, enabled = !stopping) { Text(stringResource(R.string.about_stop_settings)) }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card {
                    LabeledSwitch(stringResource(R.string.about_editor_enabled),
                        stringResource(if (enabled) R.string.about_editor_on_hint else R.string.about_editor_off_hint),
                        enabled, { value ->
                            settings.setEnabled(AboutPhoneCustomizationHook, value)
                            enabled = value
                        }, Modifier.padding(16.dp))
                }
            }
            item {
                EntryCard(stringResource(R.string.about_model_label),
                    if (config.hideModel) stringResource(R.string.about_name_hidden)
                    else config.modelName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.about_original_name),
                    onClick = { editingModel = true })
            }
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.about_search_hint)) }, singleLine = true)
            }
            item {
                Text(stringResource(R.string.about_builtin_rows), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_customization_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val builtin = AboutPhoneConfig.fields.filter { field ->
                val row = config.row(field.key)
                listOf(context.getString(field.labelRes), row?.title.orEmpty(), row?.value.orEmpty())
                    .any { it.contains(query.trim(), ignoreCase = true) }
            }
            items(builtin, key = { it.key }) { field ->
                val row = config.row(field.key)
                EntryCard(stringResource(field.labelRes), rowDescription(row),
                    onClick = { editingKey = field.key })
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.about_custom_rows), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        if (customRows.size < AboutPhoneConfig.MAX_CUSTOM_ROWS) {
                            editingKey = AboutPhoneConfig.CUSTOM_PREFIX + UUID.randomUUID()
                        } else scope.launch { snackbar.showSnackbar(context.getString(R.string.about_max_custom_reached,
                            AboutPhoneConfig.MAX_CUSTOM_ROWS)) }
                    }) {
                        Icon(Icons.Rounded.Add, null)
                        Text(stringResource(R.string.about_add_row), Modifier.padding(start = 4.dp))
                    }
                }
            }
            val filteredCustom = customRows.filter { row ->
                listOf(row.title.orEmpty(), row.value.orEmpty()).any { it.contains(query.trim(), ignoreCase = true) }
            }
            if (customRows.isEmpty() && query.isBlank()) {
                item { Text(stringResource(R.string.about_empty_custom_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(filteredCustom, key = { it.key }) { row ->
                Card {
                    EntryContent(row.title.orEmpty(), rowDescription(row), { editingKey = row.key })
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.about_custom_position, customRows.indexOf(row) + 1, customRows.size),
                            Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.labelSmall)
                        IconButton(onClick = { move(row, -1) }, enabled = row != customRows.firstOrNull()) {
                            Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.about_move_up))
                        }
                        IconButton(onClick = { move(row, 1) }, enabled = row != customRows.lastOrNull()) {
                            Icon(Icons.Rounded.ArrowDownward, stringResource(R.string.about_move_down))
                        }
                    }
                }
            }
            if (query.isNotBlank() && builtin.isEmpty() && filteredCustom.isEmpty()) {
                item { Text(stringResource(R.string.about_empty_search)) }
            }
            if (config != AboutPhoneConfig()) {
                item { TextButton(onClick = { resetAll = true }) { Text(stringResource(R.string.about_reset_all_action)) } }
            }
        }
    }
    if (resetAll) AlertDialog(onDismissRequest = { resetAll = false },
        title = { Text(stringResource(R.string.about_reset_title)) },
        text = { Text(stringResource(R.string.about_reset_text)) },
        confirmButton = { TextButton(onClick = {
            if (save(AboutPhoneConfig(), R.string.about_all_reset)) resetAll = false
        }) { Text(stringResource(R.string.about_reset_all_action)) } },
        dismissButton = { TextButton(onClick = { resetAll = false }) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
private fun EntryCard(title: String, description: String, onClick: () -> Unit) {
    Card { EntryContent(title, description, onClick) }
}

@Composable
private fun EntryContent(title: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun rowDescription(row: AboutPhoneRow?): String = when {
    row?.visibility == AboutPhoneVisibility.HIDE -> stringResource(R.string.about_entry_hidden)
    row?.value != null -> if (row.value.isEmpty()) stringResource(R.string.about_value_hidden) else row.value
    row?.title != null -> if (row.title.isEmpty()) stringResource(R.string.about_label_hidden)
        else stringResource(R.string.about_label_changed, row.title)
    row?.visibility == AboutPhoneVisibility.SHOW -> stringResource(R.string.about_visibility_show)
    else -> stringResource(R.string.about_original_text)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutPhoneRowEditor(row: AboutPhoneRow, fieldLabel: String?, isNew: Boolean,
    onBack: () -> Unit, onSave: (AboutPhoneRow) -> Unit, onRemove: () -> Unit) {
    val custom = fieldLabel == null
    var title by rememberSaveable(row.key) { mutableStateOf(row.title.orEmpty()) }
    var value by rememberSaveable(row.key) { mutableStateOf(row.value.orEmpty()) }
    var originalTitle by rememberSaveable(row.key) { mutableStateOf(!custom && row.title == null) }
    var originalValue by rememberSaveable(row.key) { mutableStateOf(!custom && row.value == null) }
    var visibilityName by rememberSaveable(row.key) { mutableStateOf(row.visibility.name) }
    var showTitleError by rememberSaveable(row.key) { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val draft = row.copy(title = if (originalTitle) null else title, value = if (originalValue) null else value,
        visibility = AboutPhoneVisibility.valueOf(visibilityName))
    val changed = draft != row
    fun leave() { if (changed) discard = true else onBack() }
    BackHandler { leave() }

    Scaffold(topBar = { EditorTopBar(if (isNew) stringResource(R.string.about_add_row) else
        fieldLabel ?: row.title.orEmpty(), ::leave) }, bottomBar = {
        Surface(modifier = Modifier.navigationBarsPadding().imePadding(), tonalElevation = 2.dp) {
            Button(onClick = {
                if (custom && title.isBlank()) showTitleError = true else onSave(draft)
            }, Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(if (isNew) R.string.about_add_row else R.string.about_save)) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (custom) Text(stringResource(R.string.about_custom_form_hint), style = MaterialTheme.typography.bodyMedium)
            if (!custom) LabeledSwitch(stringResource(R.string.about_keep_original_title), null, originalTitle, { originalTitle = it })
            if (!originalTitle) OutlinedTextField(title, { title = it.take(2000); showTitleError = false }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.about_row_title)) },
                placeholder = { Text(fieldLabel ?: stringResource(R.string.about_row_title_placeholder)) },
                singleLine = true, isError = showTitleError,
                supportingText = { if (showTitleError) Text(stringResource(R.string.about_title_required)) })
            if (!custom) LabeledSwitch(stringResource(R.string.about_keep_original_value), null, originalValue, { originalValue = it })
            if (!originalValue) OutlinedTextField(value, { value = it.take(2000) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.about_row_value)) },
                placeholder = { Text(stringResource(R.string.about_row_value_placeholder)) }, maxLines = 4,
                supportingText = { Text(stringResource(R.string.about_value_hint)) })
            Text(stringResource(R.string.about_visibility), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AboutPhoneVisibility.entries.filter { !custom || it != AboutPhoneVisibility.DEFAULT }.forEach { mode ->
                    VisibilityOption(mode, mode.name == visibilityName, onSelect = { visibilityName = mode.name })
                }
            }
            if (draft.visibility == AboutPhoneVisibility.SHOW && !custom) {
                Text(stringResource(R.string.about_show_missing_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isNew && (custom || row != AboutPhoneRow(row.key))) TextButton(onClick = onRemove) {
                Text(stringResource(if (custom) R.string.about_delete_row else R.string.about_reset_entry_action))
            }
        }
    }
    if (discard) DiscardDialog(onDismiss = { discard = false }, onDiscard = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutPhoneModelEditor(config: AboutPhoneConfig, onBack: () -> Unit, onSave: (String?, Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf(config.modelName.orEmpty()) }
    var original by rememberSaveable { mutableStateOf(config.modelName == null) }
    var shown by rememberSaveable { mutableStateOf(!config.hideModel) }
    var showNameError by rememberSaveable { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val changed = (if (original) null else name) != config.modelName || !shown != config.hideModel
    fun leave() { if (changed) discard = true else onBack() }
    BackHandler { leave() }
    Scaffold(topBar = { EditorTopBar(stringResource(R.string.about_model_label), ::leave) }, bottomBar = {
        Surface(modifier = Modifier.navigationBarsPadding().imePadding(), tonalElevation = 2.dp) {
            Button(onClick = {
                if (!original && shown && name.isBlank()) showNameError = true
                else onSave(if (original) null else name, !shown)
            },
                Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(R.string.about_save)) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.about_model_hint), style = MaterialTheme.typography.bodyMedium)
            LabeledSwitch(stringResource(R.string.about_name_show), null, shown, { shown = it })
            LabeledSwitch(stringResource(R.string.about_keep_original_name), null, original, { original = it })
            if (!original) OutlinedTextField(name, { name = it.take(2000); showNameError = false }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.about_model_input_label)) },
                placeholder = { Text(stringResource(R.string.about_custom_name_placeholder)) }, singleLine = true,
                isError = showNameError, supportingText = {
                    if (showNameError) Text(stringResource(R.string.about_name_required))
                })
        }
    }
    if (discard) DiscardDialog(onDismiss = { discard = false }, onDiscard = onBack)
}

@Composable
private fun LabeledSwitch(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun VisibilityOption(mode: AboutPhoneVisibility, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(when (mode) {
                AboutPhoneVisibility.DEFAULT -> R.string.about_visibility_default
                AboutPhoneVisibility.SHOW -> R.string.about_visibility_show
                AboutPhoneVisibility.HIDE -> R.string.about_visibility_hide
            }))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
    } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
}

@Composable
private fun DiscardDialog(onDismiss: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_discard_title)) },
        text = { Text(stringResource(R.string.about_discard_text)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.about_discard_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_continue_editing)) } })
}
