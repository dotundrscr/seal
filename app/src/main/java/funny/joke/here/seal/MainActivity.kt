package funny.joke.here.seal

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import funny.joke.here.seal.data.ConnectionRepository
import funny.joke.here.seal.ssh.SSH
import funny.joke.here.seal.ui.AddConnectionScreen
import funny.joke.here.seal.ui.AddServiceScreen
import funny.joke.here.seal.ui.ServersScreen
import funny.joke.here.seal.ui.ServicesScreen
import funny.joke.here.seal.ui.ServicePresetUi
import funny.joke.here.seal.ui.DeployedService
import funny.joke.here.seal.ui.SettingsScreen
import funny.joke.here.seal.ui.theme.SealTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_UNSPECIFIED) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        enableEdgeToEdge()
        setContent {
            SealTheme {
                SealApp()
            }
        }
    }
}

@Composable
fun SealApp() {
    val context    = LocalContext.current
    val repository = remember { ConnectionRepository(context) }
    val scope      = rememberCoroutineScope()

    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.SERVERS) }
    var connections        by remember { mutableStateOf<List<SSH>>(emptyList()) }
    var showAddConnection  by rememberSaveable { mutableStateOf(false) }
    var selectedPreset     by remember { mutableStateOf<ServicePresetUi?>(null) }
    var selectedCustomCompose by rememberSaveable { mutableStateOf(false) }
    var editingService     by remember { mutableStateOf<DeployedService?>(null) }
    var refreshServicesKey by remember { mutableStateOf(0L) }

    val currentTheme = AppCompatDelegate.getDefaultNightMode().let { mode ->
        if (mode == AppCompatDelegate.MODE_NIGHT_UNSPECIFIED) AppCompatDelegate.MODE_NIGHT_YES else mode
    }

    val configuration = LocalConfiguration.current
    val currentLocale = remember(configuration) {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) {
            locales.get(0)?.language ?: "ru"
        } else {
            configuration.locales[0].language
        }
    }

    LaunchedEffect(Unit) {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru"))
        }
    }

    LaunchedEffect(Unit) {
        connections = withContext(Dispatchers.IO) { repository.loadAll() }
    }

    BackHandler(enabled = showAddConnection) {
        showAddConnection = false
    }

    BackHandler(enabled = selectedPreset != null || selectedCustomCompose || editingService != null) {
        selectedPreset = null
        selectedCustomCompose = false
        editingService = null
    }

    if (showAddConnection) {
        AddConnectionScreen(
            onSave = { conn ->
                scope.launch {
                    withContext(Dispatchers.IO) { repository.add(conn) }
                    connections       = withContext(Dispatchers.IO) { repository.loadAll() }
                    showAddConnection = false
                }
            },
            onBack = { showAddConnection = false }
        )
    } else if (selectedPreset != null || selectedCustomCompose || editingService != null) {
        AddServiceScreen(
            preset = selectedPreset,
            editingService = editingService,
            connections = connections,
            onBack = {
                selectedPreset = null
                selectedCustomCompose = false
                editingService = null
                refreshServicesKey++
            }
        )
    } else {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.forEach { dest ->
                    item(
                        icon     = { 
                            val icon = if (dest == currentDestination) dest.selectedIcon else dest.unselectedIcon
                            Icon(icon, contentDescription = stringResource(dest.labelRes)) 
                        },
                        label    = { Text(stringResource(dest.labelRes)) },
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
                        connections = connections,
                        refreshKey = refreshServicesKey,
                        modifier = Modifier.padding(innerPadding),
                        onPresetSelected = { preset -> selectedPreset = preset },
                        onCustomComposeSelected = { selectedCustomCompose = true },
                        onEditService = { service -> editingService = service }
                    )
                    AppDestinations.SETTINGS -> SettingsScreen(
                        currentLanguage = currentLocale,
                        onLanguageSelected = { lang ->
                            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(lang)
                            AppCompatDelegate.setApplicationLocales(appLocale)
                        },
                        currentTheme = currentTheme,
                        onThemeSelected = { mode ->
                            AppCompatDelegate.setDefaultNightMode(mode)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

enum class AppDestinations(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    SERVICES(R.string.nav_services, Icons.Filled.GridView, Icons.Outlined.GridView),
    SERVERS(R.string.nav_servers,   Icons.Filled.Storage,  Icons.Outlined.Storage),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}
