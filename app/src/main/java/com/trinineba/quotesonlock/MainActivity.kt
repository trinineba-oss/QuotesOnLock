package com.trinineba.quotesonlock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Typography
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = QuotePrefs(this)
        if (prefs.enabled) QuoteScheduler.ensureScheduled(this, prefs.intervalMinutes)
        setContent {
            var theme by remember { mutableStateOf(prefs.theme) }
            var animate by remember { mutableStateOf(prefs.animateBackground) }
            QuotesTheme(theme, animate) {
                QuotesScreen(
                    prefs = prefs,
                    repo = QuoteRepository(this, prefs),
                    theme = theme,
                    animate = animate,
                    onTheme = { theme = it },
                    onAnimate = { animate = it },
                )
            }
        }
    }
}

@Composable
private fun QuotesTheme(theme: AppTheme, animate: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = theme.colorScheme() ?: when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, typography = theme.typography(Typography())) {
        Box(Modifier.fillMaxSize()) {
            ThemedBackground(theme, animate)
            content()
        }
    }
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
private fun QuotesScreen(
    prefs: QuotePrefs,
    repo: QuoteRepository,
    theme: AppTheme,
    animate: Boolean,
    onTheme: (AppTheme) -> Unit,
    onAnimate: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var interval by remember { mutableStateOf(prefs.intervalMinutes) }
    var quoteFont by remember { mutableStateOf(prefs.font) }
    var quoteSize by remember { mutableStateOf(prefs.size) }
    var quoteColor by remember { mutableStateOf(prefs.textColor) }
    var hex by rememberSaveable { mutableStateOf("") }
    var categories by remember { mutableStateOf(prefs.enabledCategories) }
    var query by rememberSaveable { mutableStateOf("") }
    var expandedGroups by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(setOf(QuoteGroup.THEME.name)) }
    var current by remember { mutableStateOf(repo.current()) }
    var canGoBack by remember { mutableStateOf(repo.hasPrevious()) }
    var custom by remember { mutableStateOf(repo.custom()) }
    var newText by rememberSaveable { mutableStateOf("") }
    var newAuthor by rememberSaveable { mutableStateOf("") }
    // Open pages of the quote browser, innermost last; empty means the main screen.
    var browse by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.listSaver<List<String>, String>(save = { it }, restore = { it })) { mutableStateOf(emptyList<String>()) }

    // The notification's "Next quote" button changes the quote while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        current = repo.current()
        canGoBack = repo.hasPrevious()
    }

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

    // Read from Compose state so the hints below update when quotes or types change.
    fun countOf(category: Category) =
        if (category == Category.CUSTOM) custom.size else repo.count(category)
    val poolEmpty = Category.entries.none { it in categories && countOf(it) > 0 }

    fun refreshNotification() {
        if (enabled) current?.let { QuoteNotifier.show(context, it) }
    }

    fun chooseFont(font: QuoteFont) {
        quoteFont = font
        prefs.font = font
        refreshNotification()
    }

    fun chooseSize(size: QuoteSize) {
        quoteSize = size
        prefs.size = size
        refreshNotification()
    }

    fun chooseColor(color: Int?) {
        quoteColor = color
        prefs.textColor = color
        refreshNotification()
    }

    fun nextQuote() {
        val quote = repo.advance() ?: return
        current = quote
        canGoBack = repo.hasPrevious()
        if (enabled) QuoteNotifier.show(context, quote)
    }

    fun previousQuote() {
        val quote = repo.previous() ?: return
        current = quote
        canGoBack = repo.hasPrevious()
        if (enabled) QuoteNotifier.show(context, quote)
    }

    fun toggleCategory(category: Category, on: Boolean) {
        categories = if (on) categories + category else categories - category
        prefs.enabledCategories = categories
        // Swap out a quote that no longer belongs to a selected category.
        val shown = current
        if (shown == null || repo.pool().none { it.text == shown.text }) nextQuote()
    }

    fun setCategories(list: List<Category>, on: Boolean) {
        categories = if (on) categories + list else categories - list.toSet()
        prefs.enabledCategories = categories
        val shown = current
        if (shown == null || repo.pool().none { it.text == shown.text }) nextQuote()
    }

    browse.lastOrNull()?.let { page ->
        QuoteBrowser(
            page = page,
            repo = repo,
            onBack = { browse = browse.dropLast(1) },
            onShow = { quote ->
                current = repo.show(quote)
                canGoBack = repo.hasPrevious()
                if (enabled) QuoteNotifier.show(context, quote)
                browse = emptyList()
            },
            onOpenAuthor = { browse = browse + BrowsePage.author(it) },
        )
        return
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
                Card(
                    modifier = Modifier.fillMaxWidth().then(
                        if (theme == AppTheme.CLASSIC) Modifier
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CardDefaults.shape)
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val quote = current
                        if (quote == null) {
                            Text(
                                if (poolEmpty) {
                                    "No quotes available. Turn on a quote type or add your own below."
                                } else {
                                    "Press \"Next quote\" to pick one."
                                }
                            )
                        } else {
                            val family = fontFamilyOf(quoteFont)
                            val textColor = quoteColor?.let { Color(it) } ?: Color.Unspecified
                            Text(
                                "“${quote.text}”",
                                fontFamily = family,
                                fontSize = quoteSize.sizeSp.sp,
                                color = textColor,
                            )
                            quote.author?.let {
                                val name = authorName(it)
                                Text(
                                    "— $it",
                                    fontFamily = family,
                                    fontSize = (quoteSize.sizeSp * 0.85f).sp,
                                    color = if (quoteColor == null && theme != AppTheme.CLASSIC) MaterialTheme.colorScheme.primary else textColor,
                                    // Tap the author to see all of their quotes.
                                    modifier = if (name != null) Modifier.clickable { browse = listOf(BrowsePage.author(name)) } else Modifier,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { previousQuote() }, enabled = canGoBack) { Text("Previous") }
                            Button(onClick = { nextQuote() }) { Text("Next quote") }
                        }
                        OutlinedButton(onClick = { quote?.let { QuoteClipboard.copy(context, it) } }, enabled = quote != null) { Text("Copy quote") }
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
                Column {
                    Text("Quote types", style = MaterialTheme.typography.titleMedium)
                    if (poolEmpty) {
                        Text(
                            "Turn on at least one type to get quotes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item(key = "modern-authors") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Modern authors still in copyright", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Short excerpts from Camus, Hesse, Murakami and others. Switch off to use only public-domain texts.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = Category.MODERN.all { it in categories },
                        onCheckedChange = { setCategories(Category.MODERN, it) },
                    )
                }
            }

            item(key = "type-search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search authors and types") },
                )
            }

            QuoteGroup.entries.forEach { group ->
                val inGroup = Category.entries.filter { it.group == group && (it == Category.CUSTOM || countOf(it) > 0) }
                val shown = inGroup.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                if (shown.isEmpty()) return@forEach
                val open = query.isNotBlank() || group == QuoteGroup.MINE || group.name in expandedGroups
                val onCount = inGroup.count { it in categories }
                item(key = "group-${group.name}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = query.isBlank() && group != QuoteGroup.MINE) {
                                expandedGroups = if (group.name in expandedGroups) expandedGroups - group.name else expandedGroups + group.name
                            }
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(group.title, style = MaterialTheme.typography.labelLarge)
                            Text(
                                "$onCount of ${inGroup.size} on",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (group != QuoteGroup.MINE) Text(if (open) "\u25BE" else "\u25B8")
                    }
                }
                if (open) {
                    if (group != QuoteGroup.MINE) {
                        item(key = "bulk-${group.name}") {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { setCategories(shown, true) }) { Text("All on") }
                                TextButton(onClick = { setCategories(shown, false) }) { Text("All off") }
                            }
                        }
                    }
                    items(shown, key = { it.key }) { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Tap the name to browse the quotes of this type.
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable(enabled = countOf(category) > 0) { browse = listOf(BrowsePage.category(category)) }
                                    .padding(vertical = 4.dp),
                            ) {
                                Text(category.label)
                                Text(
                                    "${countOf(category)} quotes \u00B7 View \u203A",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Switch(
                                checked = category in categories,
                                onCheckedChange = { toggleCategory(category, it) },
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)

                    Text("Theme", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppTheme.entries.forEach { option ->
                            FilterChip(
                                selected = theme == option,
                                onClick = {
                                    prefs.theme = option
                                    onTheme(option)
                                    refreshNotification()
                                },
                                label = { Text(option.label) },
                            )
                        }
                    }
                    theme.motionLabel?.let { label ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label)
                                Text(
                                    if (theme == AppTheme.STARRY && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                        "Needs Android 13 or newer; this phone shows the still painting."
                                    } else {
                                        "Only moves while the app is open, so it doesn't use battery in the background."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(
                                checked = animate,
                                onCheckedChange = {
                                    prefs.animateBackground = it
                                    onAnimate(it)
                                },
                            )
                        }
                    }


                    Text("Font", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuoteFont.entries.forEach { font ->
                            FilterChip(
                                selected = quoteFont == font,
                                onClick = { chooseFont(font) },
                                label = { Text(font.label, fontFamily = fontFamilyOf(font)) },
                            )
                        }
                    }

                    Text("Size", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuoteSize.entries.forEach { size ->
                            FilterChip(
                                selected = quoteSize == size,
                                onClick = { chooseSize(size) },
                                label = { Text(size.label) },
                            )
                        }
                    }

                    Text("Color", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorSwatch("System default", null, quoteColor == null) {
                            hex = ""
                            chooseColor(null)
                        }
                        COLOR_PRESETS.forEach { (name, argb) ->
                            ColorSwatch(name, Color(argb), quoteColor == argb) {
                                hex = ""
                                chooseColor(argb)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { input ->
                            hex = input.trim().removePrefix("#").take(6)
                            if (HEX_COLOR.matches(hex)) chooseColor(0xFF000000.toInt() or hex.toInt(16))
                        },
                        label = { Text("Custom color (hex, e.g. FFC107)") },
                        isError = hex.isNotEmpty() && !HEX_COLOR.matches(hex),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Android draws the notification background itself and it follows your light " +
                            "or dark theme, so pick a color that stands out against it. " +
                            "\"System default\" always stays readable.",
                        style = MaterialTheme.typography.bodySmall,
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

private val HEX_COLOR = Regex("[0-9A-Fa-f]{6}")

private fun fontFamilyOf(font: QuoteFont) = FontFamily(Typeface.create(font.family, Typeface.NORMAL))

@Composable
private fun ColorSwatch(label: String, color: Color?, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.surfaceVariant)
            .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (color == null) Text("A", style = MaterialTheme.typography.labelLarge)
    }
}
