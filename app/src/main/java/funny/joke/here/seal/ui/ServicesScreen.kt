package funny.joke.here.seal.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import funny.joke.here.seal.R
import funny.joke.here.seal.ssh.SSH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// ── Data models ──────────────────────────────────────────────────────────────

data class DockerContainer(
    val id: String,
    val names: String,
    val image: String,
    val state: String,
    val status: String,
    val ports: String,
    val labels: String = "",
    val composeFolderName: String? = null,
    val composeContent: String? = null,
    val presetName: String? = null
)

data class DeployedContainer(
    val server: SSH,
    val container: DockerContainer
)

/** Kept for backward compatibility with MainActivity navigation and edit hooks */
data class DeployedService(
    val server: SSH,
    val folderName: String,
    val composeContent: String,
    val state: String,         // "running" | "stopped" | "paused" etc.
    val status: String,        // e.g. "Up 2 hours"
    val ports: String,
    val containers: List<DockerContainer>,
    val presetName: String?    // Detected preset name if any
)

sealed class ServicesLoadState {
    object Idle : ServicesLoadState()
    object Loading : ServicesLoadState()
    data class Success(val containers: List<DeployedContainer>) : ServicesLoadState()
    data class Error(val message: String) : ServicesLoadState()
}

// ── Presets static list ──────────────────────────────────────────────────────

val servicePresets: List<ServicePresetUi> = listOf(
    ServicePresetUi(
        name = "Minecraft",
        icon = Icons.Rounded.Terrain,
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
    ),
    ServicePresetUi(
        name = "Nextcloud",
        icon = Icons.Rounded.Cloud,
        template = """
        volumes:
            nextcloud:
            db:

        services:
            db:
                image: mariadb:10.6
                restart: always
                command: --transaction-isolation=READ-COMMITTED --log-bin=binlog --binlog-format=ROW
                volumes:
                    - db:/var/lib/mysql
                environment:
                    - MYSQL_ROOT_PASSWORD=%sqlrootpassword%
                    - MYSQL_PASSWORD=%sqlpassword%
                    - MYSQL_DATABASE=nextcloud
                    - MYSQL_USER=nextcloud

            app:
                image: nextcloud
                restart: always
                ports:
                    - %target%:80
                links:
                    - db
                volumes:
                    - nextcloud:/var/www/html
                environment:
                    - MYSQL_PASSWORD=%sqlpassword%
                    - MYSQL_DATABASE=nextcloud
                    - MYSQL_USER=nextcloud
                    - MYSQL_HOST=db
        """.trimIndent(),
        fields = mapOf("target" to 8080, "sqlpassword" to "CHANGEME", "sqlrootpassword" to "CHANGEME")
    )
)

// ── SSH Services & Containers Parser ─────────────────────────────────────────

private suspend fun fetchServicesFromServer(server: SSH): List<DeployedContainer> {
    return withContext(Dispatchers.IO) {
        runCatching {
            if (!server.sessionActive()) {
                server.openSession()
            }
            // Elegant single SSH roundtrip querying both ~/seal compose directories and all system docker containers
            val raw = server.runCmd(
                arrayOf(
                    "if [ -d ~/seal ]; then",
                    "  for d in ~/seal/*/; do",
                    "    if [ -f \"\$d/compose.yml\" ] || [ -f \"\$d/docker-compose.yml\" ]; then",
                    "      echo \"=== DIR: \$(basename \"\$d\") ===\"",
                    "      if [ -f \"\$d/compose.yml\" ]; then cat \"\$d/compose.yml\"; else cat \"\$d/docker-compose.yml\"; fi",
                    "      echo \"=== END ===\"",
                    "    fi",
                    "  done",
                    "fi",
                    "echo \"=== ALL PS ===\"",
                    "docker ps -a --format json",
                    "echo \"=== ALL PS END ===\""
                )
            )
            server.closeSession()
            parseDeployedContainers(server, raw)
        }.getOrElse { e ->
            runCatching { server.closeSession() }
            emptyList()
        }
    }
}

private fun parseDeployedContainers(server: SSH, raw: String): List<DeployedContainer> {
    // 1. Parse compose configurations under ~/seal/
    val composeProjects = mutableMapOf<String, String>() // folderName -> composeContent
    val composeSections = raw.split("=== DIR: ")
    for (sec in composeSections) {
        if (sec.isBlank()) continue
        val firstLineEnd = sec.indexOf(" ===")
        if (firstLineEnd == -1) continue
        val folderName = sec.substring(0, firstLineEnd).trim()
        
        // Skip echoed commands or headers
        if (folderName.contains("$") || folderName.contains("(") || folderName.contains(")") || folderName.contains("basename") || folderName.contains("&")) {
            continue
        }
        
        val rest = sec.substring(firstLineEnd + 4)
        val endIndex = rest.indexOf("=== END ===")
        val composeContent = if (endIndex != -1) {
            rest.substring(0, endIndex).trim()
        } else {
            rest.trim()
        }
        composeProjects[folderName] = composeContent
    }

    // 2. Locate the "=== ALL PS ===" section
    val psStartIndex = raw.indexOf("=== ALL PS ===")
    val psEndIndex = raw.indexOf("=== ALL PS END ===")
    val psContent = if (psStartIndex != -1) {
        if (psEndIndex != -1) {
            raw.substring(psStartIndex + 14, psEndIndex).trim()
        } else {
            raw.substring(psStartIndex + 14).trim()
        }
    } else {
        ""
    }

    // 3. Parse all containers
    val containersList = mutableListOf<DeployedContainer>()
    psContent.lines().map { it.trim() }.filter { it.startsWith("{") && it.endsWith("}") }.forEach { line ->
        runCatching {
            val obj = JSONObject(line)
            val id = obj.optString("ID", obj.optString("Id", "?"))
            val names = obj.optString("Names", obj.optString("Name", "?"))
            val image = obj.optString("Image", "?")
            val state = obj.optString("State", "unknown").lowercase()
            val status = obj.optString("Status", "")
            val ports = obj.optString("Ports", "")
            val labels = obj.optString("Labels", "")

            // Match with compose configurations
            var matchedFolder: String? = null
            var matchedCompose: String? = null
            
            // Try to find a match in the compose folders
            val cleanName = names.trimStart('/')
            for ((folder, compose) in composeProjects) {
                val hasProjectLabel = labels.contains("com.docker.compose.project=$folder") || 
                                     (labels.contains("com.docker.compose.project.working_dir=") && labels.contains("/$folder"))
                if (hasProjectLabel || cleanName.startsWith("${folder}-") || cleanName == folder) {
                    matchedFolder = folder
                    matchedCompose = compose
                    break
                }
            }

            val presetName = matchedCompose?.let { detectPreset(it) }

            containersList.add(
                DeployedContainer(
                    server = server,
                    container = DockerContainer(
                        id = id,
                        names = names,
                        image = image,
                        state = state,
                        status = status,
                        ports = ports,
                        labels = labels,
                        composeFolderName = matchedFolder,
                        composeContent = matchedCompose,
                        presetName = presetName
                    )
                )
            )
        }
    }
    return containersList
}

@Composable
private fun containerStateColor(state: String): Color {
    return when (state) {
        "running" -> Color(0xFF4CAF50)
        "paused"  -> Color(0xFFFFC107)
        "exited", "stopped" -> Color(0xFFF44336)
        else      -> Color(0xFF9E9E9E)
    }
}

// ── Screen: ServicesScreen ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    connections: List<SSH>,
    modifier: Modifier = Modifier,
    onPresetSelected: (ServicePresetUi) -> Unit,
    onCustomComposeSelected: () -> Unit,
    onEditService: (DeployedService) -> Unit
) {
    val scope = rememberCoroutineScope()
    var loadState by remember { mutableStateOf<ServicesLoadState>(ServicesLoadState.Idle) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedContainerForDetails by remember { mutableStateOf<DeployedContainer?>(null) }

    val refreshServices = {
        loadState = ServicesLoadState.Loading
        scope.launch {
            if (connections.isEmpty()) {
                loadState = ServicesLoadState.Success(emptyList())
            } else {
                val allContainers = mutableListOf<DeployedContainer>()
                connections.forEach { conn ->
                    allContainers += fetchServicesFromServer(conn)
                }
                loadState = ServicesLoadState.Success(allContainers)
            }
        }
    }

    // Refresh lists on load or configuration change
    LaunchedEffect(connections) {
        refreshServices()
    }

    if (showAddDialog) {
        AddServiceChoiceDialog(
            onDismiss = { showAddDialog = false },
            onPresetClick = { preset ->
                showAddDialog = false
                onPresetSelected(preset)
            },
            onCustomComposeClick = {
                showAddDialog = false
                onCustomComposeSelected()
            }
        )
    }

    // Container Management Dialog
    if (selectedContainerForDetails != null) {
        ServiceDetailsDialog(
            deployed = selectedContainerForDetails!!,
            onDismiss = { selectedContainerForDetails = null },
            onEditClick = { dep ->
                selectedContainerForDetails = null
                // Reconstruct DeployedService for backward compatibility with AddServiceScreen
                val svc = DeployedService(
                    server = dep.server,
                    folderName = dep.container.composeFolderName!!,
                    composeContent = dep.container.composeContent!!,
                    state = dep.container.state,
                    status = dep.container.status,
                    ports = dep.container.ports,
                    containers = listOf(dep.container),
                    presetName = dep.container.presetName
                )
                onEditService(svc)
            },
            onOperationSuccess = {
                refreshServices()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.services_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(
                        onClick = { refreshServices() },
                        enabled = loadState !is ServicesLoadState.Loading
                    ) {
                        if (loadState is ServicesLoadState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.services_refresh))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.services_new),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = loadState) {
                is ServicesLoadState.Idle, is ServicesLoadState.Loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.services_scanning), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is ServicesLoadState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }

                is ServicesLoadState.Success -> {
                    if (connections.isEmpty()) {
                        EmptyServicesState(
                            hasServers = false,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            connections.forEach { conn ->
                                val serverContainers = state.containers.filter { it.server.id == conn.id }
                                
                                // Server Header: "Server Name (IP)"
                                item(key = "hdr_${conn.id}") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 6.dp)
                                    ) {
                                        Text(
                                            text = "${conn.name.uppercase()} (${conn.host})",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.padding(top = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                                
                                if (serverContainers.isEmpty()) {
                                    item(key = "empty_${conn.id}") {
                                        Text(
                                            text = stringResource(R.string.services_no_containers),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                    }
                                } else {
                                    items(serverContainers, key = { "svc_${it.server.id}_${it.container.id}" }) { deployed ->
                                        ContainerCard(
                                            deployed = deployed,
                                            onClick = { selectedContainerForDetails = deployed }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Card: Docker Container Card ──────────────────────────────────────────────

@Composable
private fun ContainerCard(
    deployed: DeployedContainer,
    onClick: () -> Unit
) {
    val container = deployed.container
    val stateColor = containerStateColor(container.state)

    val matchedPreset = servicePresets.firstOrNull { it.name == container.presetName }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Icon representing Preset, Compose, or Standalone Docker
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    matchedPreset != null -> matchedPreset.icon
                    container.composeFolderName != null -> Icons.Rounded.Code
                    else -> Icons.Rounded.Dns
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = container.names.trimStart('/'),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    // State dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(stateColor)
                    )
                }

                Text(
                    text = when {
                        container.presetName != null -> stringResource(R.string.services_template, container.presetName)
                        container.composeFolderName != null -> "Compose: ${container.composeFolderName}"
                        else -> "Standalone Container"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                Text(
                    text = container.image,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (container.ports.isNotBlank()) {
                    Text(
                        text = container.ports,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyServicesState(
    hasServers: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Rounded.Layers,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        )
        Text(
            text = if (hasServers) stringResource(R.string.services_empty_no_containers) else stringResource(R.string.services_empty_no_servers),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (hasServers) stringResource(R.string.services_empty_hint_deploy) 
                   else stringResource(R.string.services_empty_hint_add_server),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

// ── Dialog: AddServiceChoiceDialog ──────────────────────────────────────────

@Composable
private fun AddServiceChoiceDialog(
    onDismiss: () -> Unit,
    onPresetClick: (ServicePresetUi) -> Unit,
    onCustomComposeClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.services_add_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // List of presets
                servicePresets.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPresetClick(preset) }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = preset.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(preset.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Custom compose row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCustomComposeClick() }
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.services_custom_compose),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.servers_cancel))
                    }
                }
            }
        }
    }
}

// ── Dialog: ServiceDetailsDialog (Management + Logs) ───────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceDetailsDialog(
    deployed: DeployedContainer,
    onDismiss: () -> Unit,
    onEditClick: (DeployedContainer) -> Unit,
    onOperationSuccess: () -> Unit
) {
    val container = deployed.container
    val server = deployed.server
    val isCompose = container.composeFolderName != null

    val scope = rememberCoroutineScope()
    val loadingLogsStr = stringResource(R.string.details_loading_logs)
    var logs by remember { mutableStateOf(loadingLogsStr) }
    var isOperating by remember { mutableStateOf(false) }
    var operationError by remember { mutableStateOf<String?>(null) }
    val logsScrollState = rememberScrollState()
    var showConsole by remember { mutableStateOf(false) }
    
    val noLogsStr = stringResource(R.string.details_no_logs)
    val errorLogsStr = stringResource(R.string.details_error_logs)

    val fetchLogs = {
        scope.launch {
            logs = loadingLogsStr
            logs = withContext(Dispatchers.IO) {
                runCatching {
                    if (!server.sessionActive()) {
                        server.openSession()
                    }
                    val output = if (isCompose) {
                        server.runCmd(
                            arrayOf(
                                "cd ~/seal/${container.composeFolderName}",
                                "docker compose logs --tail=100 || docker-compose logs --tail=100"
                            )
                        )
                    } else {
                        server.runCmd(
                            arrayOf(
                                "docker logs --tail=100 ${container.id}"
                            )
                        )
                    }
                    server.closeSession()
                    if (output.isBlank()) noLogsStr else output
                }.getOrElse { e ->
                    runCatching { server.closeSession() }
                    errorLogsStr.format(e.message)
                }
            }
        }
    }

    val runContainerCommand = { cmdArray: Array<String> ->
        scope.launch {
            isOperating = true
            operationError = null
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    if (!server.sessionActive()) {
                        server.openSession()
                    }
                    server.runCmd(cmdArray)
                    server.closeSession()
                    true
                }.getOrElse { e ->
                    runCatching { server.closeSession() }
                    operationError = e.message
                    false
                }
            }
            isOperating = false
            if (success) {
                onOperationSuccess()
                fetchLogs()
            }
        }
    }

    // Auto load logs once dialog opens
    LaunchedEffect(Unit) {
        fetchLogs()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = container.names.trimStart('/'),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        if (isCompose) {
                            IconButton(onClick = { onEditClick(deployed) }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit Service Settings")
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info block
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.details_info_server), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("${server.name} (${server.host})", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.details_info_image), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(container.image, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.details_info_id), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(container.id.take(12), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                        if (isCompose) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.details_info_folder), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("~/seal/${container.composeFolderName}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.details_info_status), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(container.state.uppercase(), fontWeight = FontWeight.SemiBold, color = containerStateColor(container.state))
                        }
                        if (container.status.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.details_info_uptime), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(container.status, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Power actions (Вкл/выкл)
                Text(stringResource(R.string.details_mgmt_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                if (isOperating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val startCmd = if (isCompose) {
                        arrayOf("cd ~/seal/${container.composeFolderName}", "docker compose up -d || docker-compose up -d")
                    } else {
                        arrayOf("docker start ${container.id}")
                    }
                    Button(
                        onClick = { runContainerCommand(startCmd) },
                        enabled = !isOperating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.details_btn_start))
                    }

                    val stopCmd = if (isCompose) {
                        arrayOf("cd ~/seal/${container.composeFolderName}", "docker compose stop || docker-compose stop || docker compose down || docker-compose down")
                    } else {
                        arrayOf("docker stop ${container.id}")
                    }
                    Button(
                        onClick = { runContainerCommand(stopCmd) },
                        enabled = !isOperating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.details_btn_stop))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val restartCmd = if (isCompose) {
                        arrayOf("cd ~/seal/${container.composeFolderName}", "docker compose restart || docker-compose restart")
                    } else {
                        arrayOf("docker restart ${container.id}")
                    }
                    OutlinedButton(
                        onClick = { runContainerCommand(restartCmd) },
                        enabled = !isOperating,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.details_btn_restart))
                    }

                    val deleteCmd = if (isCompose) {
                        arrayOf(
                            "cd ~/seal/${container.composeFolderName}",
                            "docker compose down || docker-compose down",
                            "cd .. && rm -rf ${container.composeFolderName}"
                        )
                    } else {
                        arrayOf("docker rm -f ${container.id}")
                    }
                    OutlinedButton(
                        onClick = { runContainerCommand(deleteCmd) },
                        enabled = !isOperating,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.details_btn_delete))
                    }
                }

                if (operationError != null) {
                    Text(operationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (container.presetName == "Minecraft") {
                    Button(
                        onClick = { showConsole = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Rounded.Code, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.details_btn_console))
                    }
                }

                // Logs display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.details_logs_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { fetchLogs() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh logs")
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1E1E),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = logs,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = Color(0xFFE0E0E0)
                            )
                        )
                    }
                }
            }
        }
    }

    if (showConsole) {
        MinecraftConsoleDialog(
            deployed = deployed,
            onDismiss = { showConsole = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinecraftConsoleDialog(
    deployed: DeployedContainer,
    onDismiss: () -> Unit
) {
    val container = deployed.container
    val server = deployed.server
    val scope = rememberCoroutineScope()
    
    val connectingStr = stringResource(R.string.console_connecting)
    var consoleLogs by remember { mutableStateOf(connectingStr) }
    var commandInput by remember { mutableStateOf("") }
    var isSendingCommand by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var autoRefresh by remember { mutableStateOf(true) }
    
    val terminalScrollState = rememberScrollState()
    
    val emptyLogsStr = stringResource(R.string.console_empty)
    val errorLogsStr = stringResource(R.string.console_error)

    LaunchedEffect(consoleLogs) {
        terminalScrollState.scrollTo(terminalScrollState.maxValue)
    }
    
    val fetchLogs = suspend {
        runCatching {
            withContext(Dispatchers.IO) {
                if (!server.sessionActive()) {
                    server.openSession()
                }
                val output = server.runCmd(arrayOf("docker logs --tail=150 ${container.id}"))
                server.closeSession()
                output
            }
        }.onSuccess { output ->
            if (output.isNotBlank()) {
                consoleLogs = output
            } else {
                consoleLogs = emptyLogsStr
            }
        }.onFailure { e ->
            consoleLogs = errorLogsStr.format(e.message)
        }
    }
    
    LaunchedEffect(autoRefresh) {
        while (autoRefresh) {
            fetchLogs()
            kotlinx.coroutines.delay(3000)
        }
    }
    
    val sendCommand = {
        val cmd = commandInput.trim()
        if (cmd.isNotEmpty()) {
            scope.launch {
                isSendingCommand = true
                commandInput = ""
                
                withContext(Dispatchers.IO) {
                    runCatching {
                        if (!server.sessionActive()) {
                            server.openSession()
                        }
                        val escapedCmd = cmd.replace("\"", "\\\"").replace("`", "\\`")
                        server.runCmd(arrayOf(
                            "docker exec -i ${container.id} rcon-cli \"$escapedCmd\" || docker exec -i ${container.id} mc-send-to-console \"$escapedCmd\""
                        ))
                        server.closeSession()
                        
                        if (!server.sessionActive()) {
                            server.openSession()
                        }
                        val updatedLogs = server.runCmd(arrayOf("docker logs --tail=150 ${container.id}"))
                        server.closeSession()
                        
                        withContext(Dispatchers.Main) {
                            if (updatedLogs.isNotBlank()) {
                                consoleLogs = updatedLogs
                            }
                        }
                    }
                }
                isSendingCommand = false
            }
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.console_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${container.names.trimStart('/')} @ ${server.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.console_auto),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Switch(
                                checked = autoRefresh,
                                onCheckedChange = { autoRefresh = it }
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isRefreshing = true
                                    fetchLogs()
                                    isRefreshing = false
                                }
                            },
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh console")
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(terminalScrollState)
                    ) {
                        Text(
                            text = consoleLogs,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    OutlinedTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.console_placeholder),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { sendCommand() }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (commandInput.isNotEmpty()) {
                                IconButton(onClick = { commandInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear command"
                                    )
                                }
                            }
                        }
                    )
                    
                    Spacer(Modifier.width(8.dp))
                    
                    FloatingActionButton(
                        onClick = { sendCommand() },
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
                    ) {
                        if (isSendingCommand) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send Command",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
