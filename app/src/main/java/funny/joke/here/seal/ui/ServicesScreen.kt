package funny.joke.here.seal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

// ── Data model ────────────────────────────────────────────────────────────────

private sealed class ServiceItem {
    /** A pre-built service template */
    data class Preset(val ui: ServicePresetUi) : ServiceItem()
}

private val serviceList: List<ServiceItem> = buildList {
    add(
        ServiceItem.Preset(
            ServicePresetUi(
                name = "Garrys Mod",
                icon = Icons.Default.SportsEsports,
                template = """
services:
  whoami:
    image: traefik/whoami
    ports:
      - "%target%:80"
    command:
      - --name=%name%
                """.trimIndent(),
                fields = mapOf("target" to 2020, "name" to "gmod")
            )
        )
    )
    add(
        ServiceItem.Preset(
            ServicePresetUi(
                name = "Hytale",
                icon = Icons.Default.Gamepad,
                template = """
services:
  whoami:
    image: traefik/whoami
    ports:
      - "%target%:80"
    command:
      - --name=%name%
                """.trimIndent(),
                fields = mapOf("target" to 3030, "name" to "hytale")
            )
        )
    )
    add(
        ServiceItem.Preset(
            ServicePresetUi(
                name = "Minecraft",
                icon = Icons.Default.Terrain,
                template = """
services:
  minecraft:
    container_name: minecraft
    image: 'itzg/minecraft-server'
    tty: true
    stdin_open: true
    restart: unless-stopped
    ports:
      - "%port%:25565"
    environment:
      EULA: 'TRUE'
      TYPE: %type%
      VERSION: %version%
      MEMORY: %mem%
      MAX_PLAYERS: %players%
      MOTD: "%motd%"
      REGION_FILE_COMPRESSION: lz4
      JVM_XX_OPTS: '-XX:+UseZGC -XX:+ZGenerational'
      ENABLE_ROLLING_LOGS: 'true'
      ONLINE_MODE: '%online%'
    volumes:
      - './data:/data'""".trimIndent(),
                fields = mapOf(
                    "port" to 25565,
                    "type" to "VANILLA",
                    "version" to "26.1.2",
                    "mem" to "2048M",
                    "players" to "20",
                    "motd" to "A Minecraft server",
                    "online" to "TRUE"
                )
            )
        )
    )
    add(
        ServiceItem.Preset(
            ServicePresetUi(
                name = "Nextcloud",
                icon = Icons.Default.Cloud,
                template = """
services:
  whoami:
    image: traefik/whoami
    ports:
      - "%target%:80"
    command:
      - --name=%name%
""".trimIndent(),
                fields = mapOf("target" to 5050, "name" to "nextcloud")
            )
        )
    )
    add(
        ServiceItem.Preset(
            ServicePresetUi(
                name = "PostgreSQL",
                icon = Icons.Default.Storage,
                template = """
services:
  whoami:
    image: traefik/whoami
    ports:
      - "%target%:80"
    command:
      - --name=%name%
                """.trimIndent(),
                fields = mapOf("target" to 6060, "name" to "sql")
            )
        )
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    modifier: Modifier = Modifier,
    onPresetSelected: (ServicePresetUi) -> Unit = {},
    onEmptyComposeCreate: (name: String, composeYml: String) -> Unit = { _, _ -> }
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateEmptyComposeDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, yml ->
                showCreateDialog = false
                onEmptyComposeCreate(name, yml)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},          // title rendered in the list header
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                shape = RoundedCornerShape(16.dp),
                containerColor = Color(0xFFE8DEF8),
                contentColor = Color(0xFF1D1B20),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New container",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            // ── "Add services" heading ────────────────────────────────────────
            item {
                Text(
                    text = "Добавить сервис",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(
                        start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp
                    )
                )
            }

            // ── Service rows ──────────────────────────────────────────────────
            items(serviceList) { item ->
                when (item) {
                    is ServiceItem.Preset -> PresetServiceRow(item.ui, onPresetSelected)
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// EmptyComposeRow removed — replaced by FAB in ServicesScreen

// ── Row: Preset service ───────────────────────────────────────────────────────

@Composable
private fun PresetServiceRow(
    preset: ServicePresetUi,
    onPresetSelected: (ServicePresetUi) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPresetSelected(preset) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular avatar with icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = preset.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = preset.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp)
        )
    }
}

// ── Dialog: Create empty compose ──────────────────────────────────────────────

@Composable
private fun CreateEmptyComposeDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, composeYml: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var composeYml by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Новый контейнер",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = composeYml,
                    onValueChange = { composeYml = it },
                    label = { Text("compose.yml") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 20
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onCreate(name, composeYml) },
                        enabled = name.isNotBlank() && composeYml.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Создать")
                    }
                }
            }
        }
    }
}
