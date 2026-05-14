package funny.joke.here.seal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import funny.joke.here.seal.data.ConnectionRepository
import funny.joke.here.seal.ssh.SSH
import funny.joke.here.seal.ui.AddConnectionScreen
import funny.joke.here.seal.ui.AddServiceScreen
import funny.joke.here.seal.ui.ServersScreen
import funny.joke.here.seal.ui.ServicesScreen
import funny.joke.here.seal.ui.ServicePresetUi
import funny.joke.here.seal.ui.theme.SealTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SealTheme {
                SealApp()
            }
        }
    }
}

// NOTE: @PreviewScreenSizes removed — SealApp uses LocalContext + file I/O
//       which is not safe inside Compose Preview.
@Composable
fun SealApp() {
    val context    = LocalContext.current
    val repository = remember { ConnectionRepository(context) }
    val scope      = rememberCoroutineScope()

    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.SERVERS) }
    var connections        by remember { mutableStateOf<List<SSH>>(emptyList()) }
    var showAddConnection  by rememberSaveable { mutableStateOf(false) }
    var selectedPreset     by remember { mutableStateOf<ServicePresetUi?>(null) }

    // Load saved connections from disk on first composition (background thread)
    LaunchedEffect(Unit) {
        connections = withContext(Dispatchers.IO) { repository.loadAll() }
    }

    BackHandler(enabled = showAddConnection) {
        showAddConnection = false
    }

    BackHandler(enabled = selectedPreset != null) {
        selectedPreset = null
    }

    if (showAddConnection) {
        AddConnectionScreen(
            onSave = { conn ->
                // File I/O on IO dispatcher, then update state on Main
                scope.launch {
                    withContext(Dispatchers.IO) { repository.add(conn) }
                    connections       = withContext(Dispatchers.IO) { repository.loadAll() }
                    showAddConnection = false
                }
            },
            onBack = { showAddConnection = false }
        )
    } else if (selectedPreset != null) {
        AddServiceScreen(
            preset = selectedPreset!!,
            onBack = { selectedPreset = null }
        )
    } else {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.forEach { dest ->
                    item(
                        icon     = { Icon(dest.icon, contentDescription = dest.label) },
                        label    = { Text(dest.label) },
                        selected = dest == currentDestination,
                        onClick  = { currentDestination = dest }
                    )
                }
            }
        ) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                when (currentDestination) {
                    AppDestinations.SERVERS -> ServersScreen(
                        connections   = connections,
                        onAddClick    = { showAddConnection = true },
                        onDeleteClick = { id ->
                            scope.launch {
                                withContext(Dispatchers.IO) { repository.remove(id) }
                                connections = withContext(Dispatchers.IO) { repository.loadAll() }
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                    AppDestinations.SERVICES -> ServicesScreen(
                        modifier = Modifier.padding(innerPadding),
                        onPresetSelected = { preset -> selectedPreset = preset }
                    )
                    AppDestinations.SETTINGS -> Text(
                        "Settings",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    SERVICES("Services", Icons.Default.Home),
    SERVERS("Servers",   Icons.Default.Storage),
    SETTINGS("Settings", Icons.Default.Settings),
}