package funny.joke.here.seal.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import funny.joke.here.seal.R
import funny.joke.here.seal.ssh.SSH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Data models ───────────────────────────────────────────────────────────────

data class ServicePresetUi(
    val name: String,
    val icon: ImageVector,
    /** docker-compose template with %field% placeholders */
    val template: String,
    /** field-key → default value (String or Int) */
    val fields: Map<String, Any>
)

// ── Helpers for parsing fields from compose.yml ────────────────────────────────

fun detectPreset(composeContent: String): String? {
    if (composeContent.contains("# PRESET: Garrys Mod")) return "Garrys Mod"
    if (composeContent.contains("# PRESET: Hytale")) return "Hytale"
    if (composeContent.contains("# PRESET: Minecraft")) return "Minecraft"
    if (composeContent.contains("# PRESET: Nextcloud")) return "Nextcloud"
    if (composeContent.contains("# PRESET: PostgreSQL")) return "PostgreSQL"
    
    // Fallback: search for unique strings in the compose file
    if (composeContent.contains("itzg/minecraft-server")) return "Minecraft"
    if (composeContent.contains("gmod")) return "Garrys Mod"
    if (composeContent.contains("hytale")) return "Hytale"
    if (composeContent.contains("nextcloud")) return "Nextcloud"
    if (composeContent.contains("sql")) return "PostgreSQL"
    
    return null
}

fun parseFieldsFromCompose(composeContent: String, presetName: String): Map<String, String> {
    val fields = mutableMapOf<String, String>()
    when (presetName) {
        "Minecraft" -> {
            val portRegex = """-\s*['"]?(\d+):25565['"]?""".toRegex()
            portRegex.find(composeContent)?.groupValues?.get(1)?.let { fields["port"] = it }
            
            val typeRegex = """TYPE:\s*['"]?([a-zA-Z0-9_-]+)['"]?""".toRegex()
            typeRegex.find(composeContent)?.groupValues?.get(1)?.let { fields["type"] = it }
            
            val versionRegex = """VERSION:\s*['"]?([a-zA-Z0-9_\.-]+)['"]?""".toRegex()
            versionRegex.find(composeContent)?.groupValues?.get(1)?.let { fields["version"] = it }
            
            val memRegex = """MEMORY:\s*['"]?([a-zA-Z0-9_-]+)['"]?""".toRegex()
            memRegex.find(composeContent)?.groupValues?.get(1)?.let { fields["mem"] = it }
            
            val playersRegex = """MAX_PLAYERS:\s*['"]?([a-zA-Z0-9_-]+)['"]?""".toRegex()
            playersRegex.find(composeContent)?.groupValues?.get(1)?.let { fields["players"] = it }
            
            val motdRegex = """MOTD:\s*['"]?([^'"\n]+)['"]?""".toRegex()
            motdRegex.find(composeContent)?.groupValues?.get(1)?.let { fields["motd"] = it }
            
            val onlineRegex = """ONLINE_MODE:\s*['"]?([a-zA-Z0-9_-]+)['"]?""".toRegex()
            onlineRegex.find(composeContent)?.groupValues?.get(1)?.let { fields["online"] = it }
        }
        "Garrys Mod", "Hytale", "Nextcloud", "PostgreSQL" -> {
            val targetRegex = """-\s*['"]?(\d+):80['"]?""".toRegex()
            targetRegex.find(composeContent)?.groupValues?.get(1)?.let { fields["target"] = it }
            
            val nameRegex = """--name=([a-zA-Z0-9_-]+)""".toRegex()
            nameRegex.find(composeContent)?.groupValues?.get(1)?.let { fields["name"] = it }
        }
    }
    return fields
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServiceScreen(
    preset: ServicePresetUi?,
    editingService: DeployedService? = null,
    connections: List<SSH>,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isEditMode = editingService != null

    // ── Target server selection state ────────────────────────────────────────
    var selectedServer by remember {
        mutableStateOf(
            if (isEditMode) editingService!!.server
            else connections.firstOrNull()
        )
    }
    var showServerDropdown by remember { mutableStateOf(false) }

    // ── Editable state for every template field ───────────────────────────────
    val fieldValues = remember {
        mutableStateMapOf<String, String>().also { map ->
            if (isEditMode && preset != null) {
                val parsed = parseFieldsFromCompose(editingService!!.composeContent, preset.name)
                preset.fields.forEach { (k, v) ->
                    map[k] = parsed[k] ?: v.toString()
                }
            } else if (preset != null) {
                preset.fields.forEach { (k, v) -> map[k] = v.toString() }
            }
        }
    }

    var targetFolder by remember {
        mutableStateOf(
            if (isEditMode) editingService!!.folderName
            else preset?.name?.lowercase()?.replace(" ", "-") ?: "custom-service"
        )
    }

    var customComposeYml by remember {
        mutableStateOf(
            if (isEditMode && preset == null) editingService!!.composeContent
            else """
            services:
              web:
                image: nginx:alpine
                ports:
                  - "80:80"
            """.trimIndent()
        )
    }

    // ── Deployment state ─────────────────────────────────────────────────────
    var isDeploying by remember { mutableStateOf(false) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var deployError by remember { mutableStateOf<String?>(null) }
    var deploySuccess by remember { mutableStateOf(false) }
    val logScrollState = rememberScrollState()

    // Auto-scroll log to bottom
    LaunchedEffect(logLines.size) {
        logScrollState.animateScrollTo(logScrollState.maxValue)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        if (isEditMode) stringResource(R.string.add_svc_edit_title) else stringResource(R.string.add_svc_add_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── Preset or Custom header ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = preset?.icon ?: Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = preset?.name ?: stringResource(R.string.add_svc_custom),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    if (isEditMode) {
                        Text(
                            text = stringResource(R.string.add_svc_edit_mode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))

            // ── Target Server Selection ───────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.add_svc_server_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable(enabled = !isEditMode) { showServerDropdown = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedServer?.name ?: stringResource(R.string.add_svc_server_none),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            if (selectedServer != null) {
                                Text(
                                    text = "${selectedServer!!.username}@${selectedServer!!.host}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                        if (!isEditMode && connections.size > 1) {
                            Icon(
                                Icons.Rounded.ArrowDropDown,
                                contentDescription = "Select Server",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showServerDropdown,
                        onDismissRequest = { showServerDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        connections.forEach { conn ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(conn.name, fontWeight = FontWeight.Bold)
                                        Text("${conn.username}@${conn.host}:${conn.port}", fontSize = 12.sp)
                                    }
                                },
                                onClick = {
                                    selectedServer = conn
                                    showServerDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

            // ── targetFolder field ────────────────────────────────────────────
            ServiceTextField(
                label = stringResource(R.string.add_svc_folder_label),
                hint = stringResource(R.string.add_svc_folder_hint),
                value = targetFolder,
                onValueChange = { if (!isEditMode) targetFolder = it },
                leadingIcon = Icons.Rounded.Folder,
                enabled = !isEditMode
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

            // ── Per-field inputs (Preset) or Raw Text Field (Custom) ──────────
            if (preset != null) {
                preset.fields.keys.forEachIndexed { index, key ->
                    val currentValue = fieldValues[key] ?: ""
                    val isNumeric = preset.fields[key] is Int || preset.fields[key] is Long
                    ServiceTextField(
                        label = key.replaceFirstChar { it.uppercase() }.replace("_", " "),
                        value = currentValue,
                        onValueChange = { fieldValues[key] = it },
                        keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text
                    )
                    if (index < preset.fields.keys.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            } else {
                // Custom compose text editor
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.add_svc_compose_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = customComposeYml,
                        onValueChange = { customComposeYml = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Log panel ─────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = logLines.isNotEmpty() || isDeploying,
                enter = fadeIn() + expandVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        stringResource(R.string.add_svc_log_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 220.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(logScrollState)
                        ) {
                            logLines.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = when {
                                        line.startsWith("[ERROR]") -> MaterialTheme.colorScheme.error
                                        line.startsWith("[OK]") -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            if (isDeploying) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ── Error banner ──────────────────────────────────────────────────
            if (deployError != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            deployError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Finish button ─────────────────────────────────────────────────
            val errNoServer = stringResource(R.string.add_svc_err_no_server)
            val errSelectServer = stringResource(R.string.add_svc_err_select_server)
            
            Button(
                onClick = {
                    deployError = null
                    deploySuccess = false
                    logLines = emptyList()
                    isDeploying = true

                    scope.launch {
                        val conn = selectedServer
                        if (conn == null) {
                            logLines = logLines + "[ERROR] $errNoServer"
                            isDeploying = false
                            deployError = errSelectServer
                            return@launch
                        }

                        val folder = targetFolder.ifBlank { "service" }

                        // Build the final compose file content
                        var finalCompose = if (preset != null) {
                            var yml = preset.template
                            fieldValues.forEach { (field, value) ->
                                yml = yml.replace("%$field%", value)
                            }
                            "# PRESET: ${preset.name}\n$yml"
                        } else {
                            customComposeYml
                        }

                        logLines = logLines + "[INFO] Подключение к ${conn.host}…"

                        try {
                            withContext(Dispatchers.IO) {
                                if (!conn.sessionActive()) {
                                    conn.openSession()
                                }
                            }
                            logLines = logLines + "[OK]   Сессия открыта."
                        } catch (e: Exception) {
                            logLines = logLines + "[ERROR] Ошибка подключения: ${e.message}"
                            isDeploying = false
                            deployError = e.message
                            return@launch
                        }

                        logLines = logLines + "[INFO] Развертывание ~/seal/$folder/compose.yml…"

                        try {
                            val result = withContext(Dispatchers.IO) {
                                conn.runCmd(arrayOf(
                                    "mkdir -p ~/seal/$folder/",
                                    "cat << 'EOF' > ~/seal/$folder/compose.yml\n$finalCompose\nEOF",
                                    "cd ~/seal/$folder/ && (docker compose pull && docker compose up -d || docker compose up -d)"
                                ))
                            }

                            Log.i("SEAL", result)

                            result.lines()
                                .filter { it.isNotBlank() }
                                .forEach { line ->
                                    logLines = logLines + line
                                }

                            logLines = logLines + "[OK]   Готово!"
                            deploySuccess = true
                        } catch (e: Exception) {
                            Log.e("SEAL", "Deploy failed", e)
                            logLines = logLines + "[ERROR] ${e.message}"
                            deployError = e.message
                        } finally {
                            withContext(Dispatchers.IO) {
                                try { conn.closeSession() } catch (_: Exception) {}
                            }
                            isDeploying = false
                        }
                    }
                },
                enabled = !isDeploying && selectedServer != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isDeploying) stringResource(R.string.add_svc_btn_deploying) else if (deploySuccess) stringResource(R.string.add_svc_btn_done) else if (isEditMode) stringResource(R.string.add_svc_btn_save) else stringResource(R.string.add_svc_btn_run),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Reusable text field ───────────────────────────────────────────────────────

@Composable
private fun ServiceTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = hint?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) } },
            leadingIcon = leadingIcon?.let { iv ->
                { Icon(iv, contentDescription = null, modifier = Modifier.size(20.dp)) }
            },
            trailingIcon = {
                if (enabled && value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = enabled
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

private fun Modifier.size(size: Int) = size(size.dp)
