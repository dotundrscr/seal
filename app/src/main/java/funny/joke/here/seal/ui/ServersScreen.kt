package funny.joke.here.seal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import funny.joke.here.seal.R
import funny.joke.here.seal.ssh.SSH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.servers_add_description),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

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
            stringResource(R.string.servers_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.servers_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun ConnectionCard(
    connection: SSH,
    onDelete: (String) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isTesting       by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
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

            // ── Test connection button ──────────────────────────────────────
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
                                isError        -> "✗  ${result!!.take(80)}"
                                result!!.isBlank() -> "✓  ${connection.name}: connected"
                                else           -> "✓  ${connection.name}: ${result.take(60)}"
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
                                isError        -> "✗  ${result!!.take(80)}"
                                result!!.isBlank() -> "✓  ${connection.name}: connected"
                                else           -> "✓  ${connection.name}: ${result.take(33)}"
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
                                isError        -> "✗  ${result!!.take(80)}"
                                result!!.isBlank() -> "✓  ${connection.name}: connected"
                                else           -> "✓  ${connection.name}: ${result.take(19).drop(1)}"
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

            // ── Delete button ───────────────────────────────────────────────
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.servers_delete_confirm),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.servers_delete_title)) },
            text  = { Text(stringResource(R.string.servers_delete_text, connection.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(connection.id)
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.servers_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.servers_cancel)) }
            }
        )
    }
}
