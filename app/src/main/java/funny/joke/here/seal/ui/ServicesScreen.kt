package funny.joke.here.seal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ── Data model ────────────────────────────────────────────────────────────────

private sealed class ServiceItem {
    /** The "empty" template — shown with a pencil icon, no avatar circle */
    object EmptyCompose : ServiceItem()

    /** A pre-built service template */
    data class Preset(val ui: ServicePresetUi) : ServiceItem()
}

private val serviceList: List<ServiceItem> = buildList {
    add(ServiceItem.EmptyCompose)
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
    onPresetSelected: (ServicePresetUi) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},          // title rendered in the list header
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
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
                    is ServiceItem.EmptyCompose -> EmptyComposeRow()
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

// ── Row: Empty Docker Compose ─────────────────────────────────────────────────

@Composable
private fun EmptyComposeRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = "Empty Docker Compose",
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
