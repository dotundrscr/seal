package funny.joke.here.seal.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import funny.joke.here.seal.R

@Composable
fun SettingsScreen(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    currentTheme: Int,
    onThemeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Language ────────────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        
        SelectionOption(
            label = "Русский",
            selected = currentLanguage == "ru",
            onClick = { onLanguageSelected("ru") }
        )
        
        SelectionOption(
            label = "English",
            selected = currentLanguage == "en",
            onClick = { onLanguageSelected("en") }
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── Theme ───────────────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        SelectionOption(
            label = stringResource(R.string.settings_theme_system),
            selected = currentTheme == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            onClick = { onThemeSelected(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
        )

        SelectionOption(
            label = stringResource(R.string.settings_theme_dark),
            selected = currentTheme == AppCompatDelegate.MODE_NIGHT_YES,
            onClick = { onThemeSelected(AppCompatDelegate.MODE_NIGHT_YES) }
        )

        SelectionOption(
            label = stringResource(R.string.settings_theme_light),
            selected = currentTheme == AppCompatDelegate.MODE_NIGHT_NO,
            onClick = { onThemeSelected(AppCompatDelegate.MODE_NIGHT_NO) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
fun SelectionOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null // handled by Row clickable
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
