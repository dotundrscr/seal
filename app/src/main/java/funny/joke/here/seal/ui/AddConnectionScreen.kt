package funny.joke.here.seal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import funny.joke.here.seal.R
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.add_conn_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
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
                        imageVector = Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.add_conn_section_general),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))

            ConnectionTextField(
                label = stringResource(R.string.add_conn_name_label),
                value = name,
                onValueChange = { name = it; nameError = false },
                isError = nameError,
                supportingText = if (nameError) stringResource(R.string.add_conn_name_error) else null,
                leadingIcon = Icons.AutoMirrored.Rounded.Label
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

            Text(
                text = stringResource(R.string.add_conn_section_ssh),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            ConnectionTextField(
                label = stringResource(R.string.add_conn_host_label),
                value = host,
                onValueChange = { host = it; hostError = false },
                isError = hostError,
                supportingText = if (hostError) stringResource(R.string.add_conn_host_error) else null,
                leadingIcon = Icons.Rounded.Dns,
                keyboardType = KeyboardType.Uri
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

            ConnectionTextField(
                label = stringResource(R.string.add_conn_port_label),
                value = port,
                onValueChange = { port = it },
                leadingIcon = Icons.Rounded.SettingsEthernet,
                keyboardType = KeyboardType.Number
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

            Text(
                text = stringResource(R.string.add_conn_section_auth),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            ConnectionTextField(
                label = stringResource(R.string.add_conn_user_label),
                value = username,
                onValueChange = { username = it; usernameError = false },
                isError = usernameError,
                supportingText = if (usernameError) stringResource(R.string.add_conn_user_error) else null,
                leadingIcon = Icons.Rounded.Person
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

            ConnectionTextField(
                label = stringResource(R.string.add_conn_password_label),
                value = password,
                onValueChange = { password = it },
                leadingIcon = Icons.Rounded.Lock,
                keyboardType = KeyboardType.Password,
                passwordVisible = passwordVisible,
                onTogglePassword = { passwordVisible = !passwordVisible }
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    nameError     = name.isBlank()
                    hostError     = host.isBlank()
                    usernameError = username.isBlank()
                    if (!nameError && !hostError && !usernameError) {
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
                    .padding(horizontal = 20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.add_conn_save),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConnectionTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null,
    passwordVisible: Boolean? = null,
    onTogglePassword: (() -> Unit)? = null
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
                if (passwordVisible != null && onTogglePassword != null) {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            if (passwordVisible) Icons.Rounded.VisibilityOff
                            else Icons.Rounded.Visibility,
                            contentDescription = if (passwordVisible) "Hide password"
                            else "Show password",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            visualTransformation = if (passwordVisible == false) PasswordVisualTransformation()
                                   else VisualTransformation.None,
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        if (hint != null && !isError) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}



