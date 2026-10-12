package com.trinineba.quotesonlock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = QuotePrefs(this)
        if (prefs.enabled) QuoteScheduler.ensureScheduled(this, prefs.intervalMinutes)
        setContent {
            QuotesTheme {
                QuotesScreen(prefs, QuoteRepository(this, prefs))
            }
        }
    }
}

@Composable
private fun QuotesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private val INTERVALS = listOf(
    15L to "15 min",
    30L to "30 min",
    60L to "1 hour",
    180L to "3 hours",
    360L to "6 hours",
    720L to "12 hours",
    1440L to "Daily",
)

@Composable
private fun QuotesScreen(prefs: QuotePrefs, repo: QuoteRepository) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var interval by remember { mutableStateOf(prefs.intervalMinutes) }
    var includeBundled by remember { mutableStateOf(prefs.includeBundled) }
    var current by remember { mutableStateOf(repo.current()) }
    var custom by remember { mutableStateOf(repo.custom()) }
    var newText by rememberSaveable { mutableStateOf("") }
    var newAuthor by rememberSaveable { mutableStateOf("") }

    // The notification's "Next quote" button changes the quote while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { current = repo.current() }

    fun turnOn() {
        prefs.enabled = true
        enabled = true
        val quote = repo.current() ?: repo.advance()
        current = quote
        QuoteScheduler.schedule(context, interval)
        quote?.let { QuoteNotifier.show(context, it) }
    }

    fun turnOff() {
        prefs.enabled = false
        enabled = false
        QuoteScheduler.cancel(context)
        QuoteNotifier.cancel(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) turnOn() }

    fun onToggle(on: Boolean) {
        if (!on) return turnOff()
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            turnOn()
        }
    }

    fun nextQuote() {
        val quote = repo.advance()
        current = quote
        if (enabled) quote?.let { QuoteNotifier.show(context, it) }
    }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Quotes on Lock", style = MaterialTheme.typography.headlineMedium)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Show quote on lock screen", style = MaterialTheme.typography.titleMedium)
                    Switch(checked = enabled, onCheckedChange = { onToggle(it) })
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val quote = current
                        if (quote == null) {
                            Text(
                                if (repo.pool().isEmpty()) {
                                    "No quotes available. Add your own below or turn built-in quotes on."
                                } else {
                                    "Press \"Next quote\" to pick one."
                                }
                            )
                        } else {
                            Text("“${quote.text}”", style = MaterialTheme.typography.titleLarge)
                            quote.author?.let {
                                Text("— $it", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Button(onClick = { nextQuote() }) { Text("Next quote") }
                    }
                }
            }

            item {
                Column {
                    Text("Change quote every", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        INTERVALS.forEach { (minutes, label) ->
                            FilterChip(
                                selected = interval == minutes,
                                onClick = {
                                    interval = minutes
                                    prefs.intervalMinutes = minutes
                                    if (enabled) QuoteScheduler.schedule(context, minutes)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Include built-in quotes", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = includeBundled,
                        onCheckedChange = {
                            includeBundled = it
                            prefs.includeBundled = it
                        },
                    )
                }
            }

            item {
                Column {
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }) { Text("Notification settings") }
                    Text(
                        "The quote is an ongoing notification. For it to appear on the lock screen, " +
                            "set Settings > Notifications > Notifications on lock screen to show " +
                            "content and keep silent notifications enabled. Pull the notification " +
                            "down to read long quotes in full.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                Column {
                    Text("Add your own quote", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = newText,
                        onValueChange = { newText = it },
                        label = { Text("Quote") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = newAuthor,
                        onValueChange = { newAuthor = it },
                        label = { Text("Author (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Button(
                        enabled = newText.isNotBlank(),
                        onClick = {
                            repo.addCustom(newText.trim(), newAuthor.trim().ifBlank { null })
                            custom = repo.custom()
                            newText = ""
                            newAuthor = ""
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Add") }
                }
            }

            if (custom.isNotEmpty()) {
                item {
                    Text("Your quotes", style = MaterialTheme.typography.titleMedium)
                }
                items(custom, key = { it.id }) { quote ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(quote.text)
                            quote.author?.let {
                                Text("— $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TextButton(onClick = {
                            repo.removeCustom(quote.id)
                            custom = repo.custom()
                        }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
