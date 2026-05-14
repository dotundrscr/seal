package funny.joke.here.seal.ui

import android.util.Log
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import funny.joke.here.seal.data.ConnectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// ── Data model ────────────────────────────────────────────────────────────────

private sealed class ServiceItem {
    /** The "empty" template — shown with a pencil icon, no avatar circle */
    object EmptyCompose : ServiceItem()

    data class PresetSettings(val template: String, val fields: Map<String, Any>)

    /** A pre-built service template */
    data class Preset(val name: String, val icon: ImageVector, val settings: PresetSettings) :
        ServiceItem()
}

private val serviceList: List<ServiceItem> = buildList {
    add(ServiceItem.EmptyCompose)
    add(
        ServiceItem.Preset(
            "Garry's Mod",
            Icons.Default.SportsEsports,
            ServiceItem.PresetSettings(
                """
services:
  whoami:
    image: traefik/whoami
    ports:
      - "%target%:80"
    command:
      - --name=%name%
        """.trimIndent(), mapOf("target" to 2020, "name" to "gmod")
            )
        )
    )
    add(
        ServiceItem.Preset(
            "Hytale",
            Icons.Default.Gamepad,
            ServiceItem.PresetSettings(
                """
services:
  whoami:
    image: traefik/whoami
    ports:
      - "%target%:80"
    command:
      - --name=%name%
        """.trimIndent(), mapOf("target" to 3030, "name" to "hytale")
            )
        )
    )
    add(
        ServiceItem.Preset(
            "Minecraft",
            Icons.Default.Terrain,
            ServiceItem.PresetSettings(
                """
services:
  whoami:
    image: traefik/whoami
    ports:
      - "%target%:80"
    command:
      - --name=%name%
        """.trimIndent(), mapOf("target" to 4040, "name" to "minecraft")
            )
        )
    )
    add(
        ServiceItem.Preset(
            "Nextcloud",
            Icons.Default.Cloud,
            ServiceItem.PresetSettings(
                """
services:
  whoami:
    image: traefik/whoami
    ports:
      - "%target%:80"
    command:
      - --name=%name%
""".trimIndent(),
                mapOf("target" to 5050, "name" to "nextcloud")
            )
        )
    )
    add(
        ServiceItem.Preset(
            "PostgreSQL", Icons.Default.Storage, ServiceItem.PresetSettings(
                """
services:
  whoami:
    image: traefik/whoami
    ports:
      - "%target%:80"
    command:
      - --name=%name%
        """.trimIndent(), mapOf("target" to 6060, "name" to "sql")
            )
        )
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(modifier: Modifier = Modifier) {
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
                    is ServiceItem.Preset -> PresetServiceRow(item, Random.nextInt(100).toString())
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
private fun PresetServiceRow(preset: ServiceItem.Preset, targetFolder: String) {
    val repository = ConnectionRepository(LocalContext.current)
    val connections = repository.loadAll()
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (connections.isNotEmpty()) {
                    scope.launch {
                        val targetConnection = connections[0]

                        Log.i("SEAL", "filling out the template")

                        var finalCompose = preset.settings.template

                        preset.settings.fields.forEach { (field, value) ->
                            finalCompose = finalCompose.replace("%$field%", value.toString())
                        }

                        Log.i("SEAL", finalCompose)
                        Log.i("SEAL", "opening session")

                        withContext(Dispatchers.IO) {
                            if (!targetConnection.sessionActive()) {
                                targetConnection.openSession()
                            }
                        }

                        Log.i("SEAL", "trying to compose")

                        try {
                            withContext(Dispatchers.IO) {
                                val result = targetConnection.runCmd(arrayOf(
                                    $$"mkdir -p $HOME/seal/$$targetFolder/",
                                    $$"cat << 'EOF' > $HOME/seal/$$targetFolder/compose.yml\n$$finalCompose\nEOF",
                                    $$"docker compose -f $HOME/seal/$$targetFolder/compose.yml up -d"
                                ))
                                Log.i("SEAL", result)
                            }
                        } catch (e: Exception) {
                            SnackbarHostState().showSnackbar(
                                e.toString(),
                                duration = SnackbarDuration.Short
                            )
                        }
                        Log.i("SEAL", "closing session")
                        withContext(Dispatchers.IO) {
                            targetConnection.closeSession()
                        }
                    }
                }
            }
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
