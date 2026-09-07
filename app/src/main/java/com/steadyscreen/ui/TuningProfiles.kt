package com.steadyscreen.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.steadyscreen.settings.ProfileJson
import com.steadyscreen.settings.ProfileLibrary
import com.steadyscreen.settings.ProfileStore
import com.steadyscreen.settings.TuningProfile
import com.steadyscreen.settings.SettingsCodec
import com.steadyscreen.settings.ReadingSettings
import com.steadyscreen.stabilization.StabilizationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun TuningProfiles(config: StabilizationConfig, onConfig: (StabilizationConfig) -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { ProfileStore(context) }
    val loaded = remember(store) { runCatching { store.load() } }
    var library by remember(store) { mutableStateOf(loaded.getOrDefault(ProfileLibrary())) }
    var loadError by remember(store) { mutableStateOf(loaded.exceptionOrNull()?.let { it.message ?: "Unreadable data" }) }
    var status by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var clearing by rememberSaveable { mutableStateOf(false) }
    var paste by rememberSaveable { mutableStateOf(false) }
    var importedJson by rememberSaveable { mutableStateOf("") }
    // Capture the actual values when export is requested, even if the picker recreates the activity.
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selected = library.selected
    val snapshot = TuningProfile(id = "current-export", name = selected?.name ?: "Current settings",
        notes = selected?.notes ?: "", config = config)

    fun saveLibrary(updated: ProfileLibrary) {
        store.save(updated)
        library = updated
    }
    fun prepareImport(text: String): String? = try {
        // Parse first; no profile or current settings change until the user saves the import.
        ProfileJson.import(text)
        importedJson = text
        editing = "import"
        null
    } catch (error: Exception) {
        "Cannot import profile: ${error.message ?: "invalid JSON"}"
    }

    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val document = pendingExport
        pendingExport = null
        if (uri != null && document != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: error("The selected file cannot be opened.")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(document) }
                }
                status = "Profile exported."
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                status = "Export failed: ${error.message}"
            } finally {
                busy = false
            }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val document = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: error("The selected file cannot be opened.")
                    val bytes = stream.use { it.readNBytes(ProfileJson.MaxImportBytes + 1) }
                    require(bytes.size <= ProfileJson.MaxImportBytes) { "Profile file is too large (32 KiB maximum)." }
                    bytes.toString(Charsets.UTF_8)
                }
                status = prepareImport(document) ?: "Review the imported profile before saving."
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                status = "Import failed: ${error.message}"
            } finally {
                busy = false
            }
        }
    }

    Column {
        Text("Profiles", style = MaterialTheme.typography.titleMedium)
        Text("Profiles save tuning values. Reading text and ON/OFF stay as they are.",
            style = MaterialTheme.typography.bodySmall)
        if (loadError != null) {
            Text("Saved profiles could not be loaded: $loadError", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { clearing = true }) { Text("Clear unreadable profiles") }
        }
        Box {
            OutlinedButton(onClick = { menu = true }, enabled = library.profiles.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth()) {
                Text(selected?.let { it.name + if (it.config != config) " (modified)" else "" }
                    ?: "Select profile")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                library.profiles.forEach { profile ->
                    DropdownMenuItem(text = { Text(profile.name) }, onClick = {
                        saveLibrary(library.select(profile.id))
                        onConfig(profile.config)
                        menu = false
                        status = "Applied ${profile.name}."
                    })
                }
            }
        }
        if (selected?.notes?.isNotBlank() == true) Text(selected.notes, style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick = { editing = "new" }, enabled = loadError == null && !busy) { Text("Save as new") }
            TextButton(onClick = { editing = "update" }, enabled = selected != null && !busy) { Text("Update profile") }
        }
        if (selected != null) {
            TextButton(onClick = { deleting = true }, enabled = !busy) { Text("Delete profile") }
        }
        Text("Export current tuning", style = MaterialTheme.typography.labelLarge)
        Row {
            TextButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("SteadyScreen tuning profile", ProfileJson.export(snapshot)))
                status = "JSON copied for documentation or prompts."
            }, enabled = !busy) { Text("Copy JSON") }
            TextButton(onClick = {
                pendingExport = ProfileJson.export(snapshot)
                exportFile.launch("steadyscreen-profile.json")
            }, enabled = !busy) { Text("Export file") }
        }
        Row {
            TextButton(onClick = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                enabled = loadError == null && !busy) { Text("Import file") }
            TextButton(onClick = { paste = true }, enabled = loadError == null && !busy) { Text("Paste JSON") }
        }
        if (busy) Text("Working…", style = MaterialTheme.typography.bodySmall)
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
    }

    if (editing != null) {
        val mode = editing
        val initial = remember(mode, importedJson) {
            when (mode) {
                "import" -> ProfileJson.import(importedJson)
                "update" -> checkNotNull(selected).copy(config = config)
                else -> TuningProfile(name = "New profile", config = config)
            }
        }
        ProfileEditor(initial, if (mode == "import") "Save and apply" else "Save profile",
            preview = mode == "import", onDismiss = { editing = null }) {
            try {
                saveLibrary(library.save(it))
                if (mode == "import") onConfig(it.config)
                status = "Saved ${it.name}."
                editing = null
                null
            } catch (error: Exception) {
                error.message ?: "Could not save profile."
            }
        }
    }
    if (paste) {
        PasteProfileDialog(onDismiss = { paste = false }) {
            prepareImport(it).also { error -> if (error == null) paste = false }
        }
    }
    if (deleting || clearing) {
        AlertDialog(onDismissRequest = { deleting = false; clearing = false },
            title = { Text(if (clearing) "Clear unreadable profiles?" else "Delete ${selected?.name}?") },
            text = { Text("Current tuning values and reading text will be kept.") },
            confirmButton = {
                TextButton(onClick = {
                    saveLibrary(if (clearing) ProfileLibrary() else library.delete(checkNotNull(selected).id))
                    loadError = null
                    deleting = false
                    clearing = false
                    status = "Profile storage updated."
                }) { Text(if (clearing) "Clear profiles" else "Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false; clearing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProfileEditor(initial: TuningProfile, confirm: String, preview: Boolean,
    onDismiss: () -> Unit, onSave: (TuningProfile) -> String?) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Save tuning profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Profile name") }, singleLine = true,
                    supportingText = { Text("${name.length}/80") })
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes / test conditions") }, maxLines = 4,
                    supportingText = { Text("${notes.length}/2000") })
                if (preview) {
                    Text("Tuning values to apply", style = MaterialTheme.typography.titleSmall)
                    Text(SettingsCodec.encode(ReadingSettings(config = initial.config))
                        .filterKeys { it != "enabled" && it != "showDebug" }
                        .entries.joinToString("\n") { (key, value) -> "$key: $value" },
                        style = MaterialTheme.typography.bodySmall)
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { error = onSave(initial.copy(name = name.trim(), notes = notes)) },
            enabled = name.isNotBlank() && name.trim().length <= 80 && notes.length <= 2000) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasteProfileDialog(onDismiss: () -> Unit, onImport: (String) -> String?) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Import profile JSON") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(text, { text = it }, label = { Text("Paste exported JSON") }, minLines = 4, maxLines = 8)
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { error = onImport(text) }, enabled = text.isNotBlank()) { Text("Review import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
