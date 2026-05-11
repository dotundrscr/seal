package funny.joke.here.seal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import funny.joke.here.seal.ssh.SSH

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConnectionScreen(
    onSave: (SSH) -> Unit,
    onBack: () -> Unit
) {
    var name            by remember { mutableStateOf("") }
    var host            by remember { mutableStateOf("") }
    var port            by remember { mutableStateOf("22") }
    var username        by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var nameError     by remember { mutableStateOf(false) }
    var hostError     by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Connection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── General ──────────────────────────────────────────────────────
            SectionLabel("General")

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("Connection name") },
                placeholder = { Text("My Home Server") },
                leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                isError = nameError,
                supportingText = { if (nameError) Text("Name is required") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ── SSH ───────────────────────────────────────────────────────────
            SectionLabel("SSH")

            OutlinedTextField(
                value = host,
                onValueChange = { host = it; hostError = false },
                label = { Text("Host / IP address") },
                placeholder = { Text("192.168.1.100") },
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                isError = hostError,
                supportingText = { if (hostError) Text("Host is required") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                placeholder = { Text("22") },
                leadingIcon = { Icon(Icons.Default.SettingsEthernet, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // ── Auth ──────────────────────────────────────────────────────────
            SectionLabel("Authentication")

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; usernameError = false },
                label = { Text("Username") },
                placeholder = { Text("root") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                isError = usernameError,
                supportingText = { if (usernameError) Text("Username is required") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password"
                            else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    nameError     = name.isBlank()
                    hostError     = host.isBlank()
                    usernameError = username.isBlank()
                    if (!nameError && !hostError && !usernameError) {
                        // Positional args — SSH is a Java class
                        onSave(
                            SSH(
                                name.trim(),
                                host.trim(),
                                port.toIntOrNull() ?: 22,
                                username.trim(),
                                password
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save connection")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
    HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
}
