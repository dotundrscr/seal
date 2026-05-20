package funny.joke.here.seal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import funny.joke.here.seal.ssh.SSH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// ── Data model ─────────────────────────────────────────────────────────────

data class DockerContainer(
    val id: String,
    val names: String,
    val image: String,
    val state: String,   // "running" | "exited" | "paused" | etc.
    val status: String,  // "Up 2 hours" | "Exited (0) 5 min ago" | etc.
    val ports: String
)

sealed class DockerLoadState {
    object Idle      : DockerLoadState()
    object Loading   : DockerLoadState()
    data class Success(val containers: List<DockerContainer>) : DockerLoadState()
    data class Error(val message: String) : DockerLoadState()
}

// ── Helpers ─────────────────────────────────────────────────────────────────

/** Parse multi-line output of `docker ps --format json` (one JSON object per line). */
private fun parseDockerPs(raw: String): List<DockerContainer> {
    val containers = mutableListOf<DockerContainer>()
    // Each line is a JSON object; filter out shell prompt garbage
    raw.lines()
        .map { it.trim() }
        .filter { it.startsWith("{") && it.endsWith("}") }
        .forEach { line ->
            runCatching {
                val obj = JSONObject(line)
                containers += DockerContainer(
                    id     = obj.optString("ID", obj.optString("Id", "?")),
                    names  = obj.optString("Names", obj.optString("Name", "?")),
                    image  = obj.optString("Image", "?"),
                    state  = obj.optString("State", "unknown").lowercase(),
                    status = obj.optString("Status", ""),
                    ports  = obj.optString("Ports", "")
                )
            }
        }
    return containers
}

@Composable
private fun containerStateColor(state: String): Color {
    return when (state) {
        "running" -> Color(0xFF4CAF50)
        "paused"  -> Color(0xFFFFC107)
        "exited"  -> Color(0xFFF44336)
        else      -> Color(0xFF9E9E9E)
    }
}

// ── Main screen ─────────────────────────────────────────────────────────────

@Composable
fun ServersScreen(
    connections: List<SSH>,
    onAddClick: () -> Unit,
    onDeleteClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = modifier.fillMaxSize()) {

        if (connections.isEmpty()) {
            EmptyState(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(connections, key = { it.id }) { conn ->
                    ConnectionCard(
                        connection = conn,
                        onDelete = onDeleteClick,
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }

        // Snackbar — above FAB
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )

        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add server")
        }
    }
}

// ── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.Storage,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        )
        Text(
            "No servers yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Tap + to add your first server",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

// ── Connection card ──────────────────────────────────────────────────────────

@Composable
fun ConnectionCard(
    connection: SSH,
    onDelete: (String) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isTesting        by remember { mutableStateOf(false) }
    var expanded         by remember { mutableStateOf(false) }
    var dockerState      by remember { mutableStateOf<DockerLoadState>(DockerLoadState.Idle) }
    val scope = rememberCoroutineScope()

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "chevron"
    )

    // Load containers whenever the card expands
    LaunchedEffect(expanded) {
        if (expanded && dockerState !is DockerLoadState.Loading) {
            dockerState = DockerLoadState.Loading
            dockerState = withContext(Dispatchers.IO) {
                runCatching {
                    connection.openSession()
                    val raw = connection.runCmd(
                        arrayOf("docker ps --format json")
                    )
                    connection.closeSession()
                    val containers = parseDockerPs(raw)
                    DockerLoadState.Success(containers)
                }.getOrElse { e ->
                    runCatching { connection.closeSession() }
                    DockerLoadState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // ── Top row ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Server icon
                Icon(
                    Icons.Default.Computer,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.width(12.dp))

                // Name + address
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connection.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${connection.username}@${connection.host}:${connection.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // ── Test connection button ───────────────────────────────────
                IconButton(
                    onClick = {
                        if (!isTesting) {
                            isTesting = true
                            scope.launch {
                                var isError = false

                                var result = withContext(Dispatchers.IO) {
                                    connection.openSession()
                                    try {
                                        connection.runCmd(arrayOf("uname -a"))
                                    } catch (e: Exception) {
                                        isError = true
                                        if (e.message != null) {
                                            return@withContext e.message
                                        } else {
                                            return@withContext "unknown error"
                                        }
                                    }
                                }

                                var message = when {
                                    isError            -> "✗  ${result!!.take(80)}"
                                    result!!.isBlank() -> "✓  ${connection.name}: connected"
                                    else               -> "✓  ${connection.name}: ${result.take(60)}"
                                }
                                snackbarHostState.showSnackbar(
                                    message  = message,
                                    duration = SnackbarDuration.Short
                                )

                                result = withContext(Dispatchers.IO) {
                                    try {
                                        connection.runCmd(arrayOf("docker info"))
                                    } catch (e: Exception) {
                                        isError = true
                                        if (e.message != null) {
                                            return@withContext e.message
                                        } else {
                                            return@withContext "unknown error"
                                        }
                                    }
                                }

                                message = when {
                                    isError            -> "✗  ${result!!.take(80)}"
                                    result!!.isBlank() -> "✓  ${connection.name}: connected"
                                    else               -> "✓  ${connection.name}: ${result.take(33)}"
                                }

                                snackbarHostState.showSnackbar(
                                    message  = message,
                                    duration = SnackbarDuration.Short
                                )

                                result = withContext(Dispatchers.IO) {
                                    try {
                                        connection.runCmd(arrayOf("docker run -q --rm hello-world"))
                                    } catch (e: Exception) {
                                        isError = true
                                        if (e.message != null) {
                                            return@withContext e.message
                                        } else {
                                            return@withContext "unknown error"
                                        }
                                    }
                                }

                                message = when {
                                    isError            -> "✗  ${result!!.take(80)}"
                                    result!!.isBlank() -> "✓  ${connection.name}: connected"
                                    else               -> "✓  ${connection.name}: ${result.take(19).drop(1)}"
                                }

                                isTesting = false
                                withContext(Dispatchers.IO) {
                                    connection.closeSession()
                                }

                                snackbarHostState.showSnackbar(
                                    message  = message,
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    enabled = !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Test connection",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // ── Delete button ────────────────────────────────────────────
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                // ── Expand / collapse chevron ────────────────────────────────
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Show containers",
                        modifier = Modifier.rotate(chevronRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Docker containers section ────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit  = shrinkVertically(tween(250)) + fadeOut(tween(200))
            ) {
                DockerContainersSection(
                    state = dockerState,
                    onRefresh = {
                        dockerState = DockerLoadState.Loading
                        scope.launch {
                            dockerState = withContext(Dispatchers.IO) {
                                runCatching {
                                    connection.openSession()
                                    val raw = connection.runCmd(
                                        arrayOf("docker ps --format json")
                                    )
                                    connection.closeSession()
                                    DockerLoadState.Success(parseDockerPs(raw))
                                }.getOrElse { e ->
                                    runCatching { connection.closeSession() }
                                    DockerLoadState.Error(e.message ?: "Unknown error")
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon    = { Icon(Icons.Default.Delete, contentDescription = null) },
            title   = { Text("Remove server?") },
            text    = { Text("\"${connection.name}\" will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(connection.id)
                    showDeleteDialog = false
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Docker containers section ─────────────────────────────────────────────────

@Composable
private fun DockerContainersSection(
    state: DockerLoadState,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Dns,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Docker Containers",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            // Refresh button
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(28.dp),
                enabled = state !is DockerLoadState.Loading
            ) {
                if (state is DockerLoadState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh containers",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))

        when (state) {
            DockerLoadState.Idle -> {
                // Should not reach here since we load on expand
                Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            DockerLoadState.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Fetching containers…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is DockerLoadState.Error -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = state.message.take(120),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            is DockerLoadState.Success -> {
                if (state.containers.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "No running containers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.containers.forEach { container ->
                            ContainerRow(container)
                        }
                    }
                }
            }
        }
    }
}

// ── Single container row ──────────────────────────────────────────────────────

@Composable
private fun ContainerRow(container: DockerContainer) {
    val stateColor = containerStateColor(container.state)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // State indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(stateColor)
        )

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Container name
            Text(
                text = container.names.trimStart('/'),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Image
            Text(
                text = container.image,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (container.ports.isNotBlank()) {
                Text(
                    text = container.ports,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Status badge
        Surface(
            color = stateColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = container.state,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = stateColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}


