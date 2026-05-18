package funny.joke.here.seal.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import funny.joke.here.seal.data.ConnectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Data exposed to callers ───────────────────────────────────────────────────

data class ServicePresetUi(
    val name: String,
    val icon: ImageVector,
    /** docker-compose template with %field% placeholders */
    val template: String,
    /** field-key → default value (String or Int) */
    val fields: Map<String, Any>
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServiceScreen(
    preset: ServicePresetUi,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { ConnectionRepository(context) }
    val scope = rememberCoroutineScope()

    // ── Editable state for every template field ───────────────────────────────
    val fieldValues = remember {
        mutableStateMapOf<String, String>().also { map ->
            preset.fields.forEach { (k, v) -> map[k] = v.toString() }
        }
    }
    var targetFolder by remember { mutableStateOf(preset.name.lowercase().replace(" ", "-")) }

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        "Service settings",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
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

            // ── Preset header ─────────────────────────────────────────────────
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
                        imageVector = preset.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))

            // ── targetFolder field ────────────────────────────────────────────
            ServiceTextField(
                label = "Target Folder",
                hint = "Folder inside ~/seal/ on the server",
                value = targetFolder,
                onValueChange = { targetFolder = it },
                leadingIcon = Icons.Default.Folder
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

            // ── Per-field inputs ──────────────────────────────────────────────
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
                        "Deployment log",
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
                            Icons.Default.Close,
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
            Button(
                onClick = {
                    deployError = null
                    deploySuccess = false
                    logLines = emptyList()
                    isDeploying = true

                    scope.launch {
                        val connections = withContext(Dispatchers.IO) { repository.loadAll() }

                        if (connections.isEmpty()) {
                            logLines = logLines + "[ERROR] No SSH connections configured."
                            isDeploying = false
                            deployError = "Add an SSH server first."
                            return@launch
                        }

                        val conn = connections[0]
                        val folder = targetFolder.ifBlank { "service" }

                        // Build the final compose file content
                        var finalCompose = preset.template
                        fieldValues.forEach { (field, value) ->
                            finalCompose = finalCompose.replace("%$field%", value)
                        }

                        logLines = logLines + "[INFO] Connecting to ${conn.host}…"

                        try {
                            withContext(Dispatchers.IO) {
                                if (!conn.sessionActive()) {
                                    conn.openSession()
                                }
                            }
                            logLines = logLines + "[OK]   Session opened."
                        } catch (e: Exception) {
                            logLines = logLines + "[ERROR] Connection failed: ${e.message}"
                            isDeploying = false
                            deployError = e.message
                            return@launch
                        }

                        logLines = logLines + "[INFO] Creating folder ~/seal/$folder/ …"

                        try {
                            val result = withContext(Dispatchers.IO) {
                                conn.runCmd(arrayOf(
                                    $$"mkdir -p $HOME/seal/$$folder/",
                                    $$"cat << 'EOF' > $HOME/seal/$$folder/compose.yml\n$$finalCompose\nEOF",
                                    $$"docker compose -f $HOME/seal/$$folder/compose.yml up -d"
                                ))
                            }

                            Log.i("SEAL", result)

                            // Emit each non-blank line from the SSH output into the log
                            result.lines()
                                .filter { it.isNotBlank() }
                                .forEach { line ->
                                    logLines = logLines + line
                                }

                            logLines = logLines + "[OK]   Done."
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
                enabled = !isDeploying,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isDeploying) "Deploying…" else if (deploySuccess) "Done!" else "Finish",
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
    keyboardType: KeyboardType = KeyboardType.Text
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
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
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
