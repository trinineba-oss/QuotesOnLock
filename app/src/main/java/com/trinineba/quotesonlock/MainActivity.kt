package com.trinineba.quotesonlock

import android.Manifest
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    companion object {
        /** Opens About & sources, where the update card is (from the "update available" notification). */
        const val EXTRA_OPEN_UPDATES = "open_updates"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = QuotePrefs(this)
        if (prefs.enabled) QuoteScheduler.ensureScheduled(this, prefs.intervalMinutes)
        Updater.schedule(this, prefs.updateCheck)
        val openUpdates = intent.getBooleanExtra(EXTRA_OPEN_UPDATES, false)
        setContent {
            var theme by remember { mutableStateOf(prefs.theme) }
            var animate by remember { mutableStateOf(prefs.animateBackground) }
            var cardOpacity by remember { mutableStateOf(prefs.cardOpacity) }
            var auroraIntensity by remember { mutableStateOf(prefs.auroraIntensity) }
            QuotesTheme(theme, animate, cardOpacity, auroraIntensity) {
                QuotesScreen(
                    prefs = prefs,
                    repo = QuoteRepository(this, prefs),
                    theme = theme,
                    animate = animate,
                    onTheme = { theme = it },
                    onAnimate = { animate = it },
                    cardOpacity = cardOpacity,
                    onCardOpacity = { cardOpacity = it },
                    auroraIntensity = auroraIntensity,
                    onAuroraIntensity = { auroraIntensity = it },
                    openUpdates = openUpdates,
                )
            }
        }
    }
}

@Composable
private fun QuotesTheme(theme: AppTheme, animate: Boolean, cardOpacity: Float, auroraIntensity: Float, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = theme.colorScheme(cardOpacity) ?: when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, typography = theme.typography(Typography())) {
        Box(Modifier.fillMaxSize()) {
            ThemedBackground(theme, animate, auroraIntensity)
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
    cardOpacity: Float,
    onCardOpacity: (Float) -> Unit,
    auroraIntensity: Float,
    onAuroraIntensity: (Float) -> Unit,
    openUpdates: Boolean = false,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var interval by remember { mutableStateOf(prefs.intervalMinutes) }
    var allowRepeats by remember { mutableStateOf(prefs.allowRepeats) }
    var cardSoftness by remember { mutableStateOf(prefs.cardSoftness) }
    var quoteFont by remember { mutableStateOf(prefs.font) }
    var quoteSize by remember { mutableStateOf(prefs.size) }
    var quoteColor by remember { mutableStateOf(prefs.textColor) }
    var lockTheme by remember { mutableStateOf(prefs.lockTheme) }
    var hex by rememberSaveable { mutableStateOf("") }
    var categories by remember { mutableStateOf(prefs.enabledCategories) }
    var query by rememberSaveable { mutableStateOf("") }
    var expandedGroups by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(setOf(QuoteGroup.THEME.name)) }
    var current by remember { mutableStateOf(repo.current()) }
    var canGoBack by remember { mutableStateOf(repo.hasPrevious()) }
    var custom by remember { mutableStateOf(repo.custom()) }
    var favorites by remember { mutableStateOf(repo.favorites()) }
    var favoritesOnly by remember { mutableStateOf(prefs.favoritesOnly) }
    var newText by rememberSaveable { mutableStateOf("") }
    var newAuthor by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf(if (openUpdates) Page.ABOUT else Page.HOME) }
    val scope = rememberCoroutineScope()
    val currentBuild = remember { Updater.currentBuild(context) }
    var updateCheck by remember { mutableStateOf(prefs.updateCheck) }
    var latest by remember { mutableStateOf<Release?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloaded by remember { mutableStateOf<java.io.File?>(null) }
    var updateFailed by remember { mutableStateOf(false) }

    fun failureText(e: Exception, what: String): String = when (e) {
        is java.net.UnknownHostException ->
            "Your phone couldn't reach github.com for this app. Turn on network access for it in Settings > Apps > " +
                "Quotes on Lock (\"Allow network usage\", or Mobile data and Wi-Fi), and check that Private DNS or an " +
                "ad blocker isn't blocking GitHub. Or download it in your browser below."
        is java.net.SocketTimeoutException -> "GitHub took too long to answer. Try again, or download it in your browser below."
        else -> "Couldn't $what: ${e.message ?: e.javaClass.simpleName}"
    }
    var wallpaperQuote by remember { mutableStateOf(prefs.wallpaperShowQuote) }
    var wallpaperPosition by remember { mutableStateOf(prefs.wallpaperQuotePosition) }
    val sources = remember { readSources(context) }
    val subjectCounts = remember { repo.bundled().flatMap { it.subjects }.groupingBy { it }.eachCount() }
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

    fun checkForUpdate() {
        updateBusy = true
        updateFailed = false
        updateStatus = "Checking GitHub…"
        scope.launch {
            try {
                val release = withContext(Dispatchers.IO) { Updater.fetchLatest() }
                latest = release
                updateStatus = if (release.build > currentBuild) {
                    "Build ${release.build} is available."
                } else {
                    "You have the latest build."
                }
            } catch (e: Exception) {
                updateStatus = failureText(e, "check GitHub")
                updateFailed = true
            }
            updateBusy = false
        }
    }

    fun installUpdate(release: Release) {
        downloaded?.let { apk ->
            if (Updater.canInstall(context)) Updater.install(context, apk) else Updater.openInstallPermission(context)
            return
        }
        updateBusy = true
        downloadProgress = 0f
        updateStatus = "Downloading build ${release.build}…"
        scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    Updater.download(context, release) { downloadProgress = it }
                }
                downloaded = apk
                if (Updater.canInstall(context)) {
                    updateStatus = "Downloaded. Confirm the install when Android asks."
                    Updater.install(context, apk)
                } else {
                    updateStatus = "Downloaded. Allow \"Install unknown apps\" for Quotes on Lock, then come back and tap Install."
                    Updater.openInstallPermission(context)
                }
            } catch (e: Exception) {
                updateStatus = failureText(e, "download the update")
                updateFailed = true
            }
            downloadProgress = null
            updateBusy = false
        }
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

    fun toggleFavorite(quote: Quote) {
        repo.toggleFavorite(quote)
        favorites = repo.favorites()
    }

    fun setFavoritesOnly(on: Boolean) {
        favoritesOnly = on
        prefs.favoritesOnly = on
        val shown = current
        if (on && (shown == null || favorites.none { it.text == shown.text })) nextQuote()
    }

    browse.lastOrNull()?.let { bp ->
        QuoteBrowser(
            page = bp,
            repo = repo,
            onBack = {
                browse = browse.dropLast(1)
                favorites = repo.favorites()
            },
            onShow = { quote ->
                current = repo.show(quote)
                canGoBack = repo.hasPrevious()
                if (enabled) QuoteNotifier.show(context, quote)
                browse = emptyList()
                favorites = repo.favorites()
                page = Page.HOME
            },
            onOpenAuthor = { browse = browse + BrowsePage.author(it) },
        )
        return
    }

    BackHandler(enabled = page != Page.HOME) { page = Page.HOME }

    fun LazyListScope.header() {
        item(key = "header-${page.name}") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(onClickLabel = "Back") { page = Page.HOME },
                    contentAlignment = Alignment.Center,
                ) { Text("←", style = MaterialTheme.typography.titleLarge) }
                Text(
                    page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
        }
    }

    fun LazyListScope.homePage() {
        item(key = "home-title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Quotes on Lock", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Text("On lock screen", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Switch(checked = enabled, onCheckedChange = { onToggle(it) })
            }
        }
        item(key = "home-quote") {
            Card(
                modifier = Modifier.fillMaxWidth().then(
                    if (theme == AppTheme.CLASSIC) Modifier
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CardDefaults.shape)
                ),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val quote = current
                    if (quote == null) {
                        Text(
                            if (poolEmpty) {
                                "No quotes available. Turn on a quote type in Quotes & authors, or add your own."
                            } else {
                                "Press \"Next\" to pick a quote."
                            }
                        )
                    } else {
                        val family = fontFamilyOf(quoteFont)
                        val textColor = quoteColor?.let { Color(it) } ?: Color.Unspecified
                        Text(
                            "“${quote.text}”",
                            fontFamily = family,
                            fontSize = (quoteSize.sizeSp + 2).sp,
                            color = textColor,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val author = quote.author
                            if (author != null) {
                                val name = authorName(author)
                                Text(
                                    if (name != null) "— $author ›" else "— $author",
                                    fontFamily = family,
                                    fontSize = (quoteSize.sizeSp * 0.9f).sp,
                                    color = if (quoteColor == null && theme != AppTheme.CLASSIC) MaterialTheme.colorScheme.primary else textColor,
                                    // Tap the author to see all of their quotes.
                                    modifier = Modifier.weight(1f).then(
                                        if (name != null) {
                                            Modifier.clickable(onClickLabel = "All quotes by $name") { browse = listOf(BrowsePage.author(name)) }
                                        } else Modifier
                                    ),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            val favorite = favorites.any { it.text == quote.text }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable(onClickLabel = if (favorite) "Remove from favourites" else "Add to favourites") { toggleFavorite(quote) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (favorite) "♥" else "♡",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = if (favorite) Color(0xFFE0566B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { previousQuote() }, enabled = canGoBack, modifier = Modifier.weight(1f)) { Text("Previous") }
                        Button(onClick = { nextQuote() }, modifier = Modifier.weight(1f)) { Text("Next") }
                        OutlinedButton(
                            onClick = { quote?.let { QuoteClipboard.copy(context, it) } },
                            enabled = quote != null,
                            modifier = Modifier.weight(1f),
                        ) { Text("Copy") }
                    }
                    OutlinedButton(
                        onClick = { quote?.let { QuoteShare.share(context, it) } },
                        enabled = quote != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Share as image") }
                }
            }
        }
        val typeCount = Category.entries.count { it != Category.CUSTOM && countOf(it) > 0 }
        val onCount = Category.entries.count { it in categories && countOf(it) > 0 }
        val tiles = listOf(
            Tile("❝", "Quotes & authors", "$onCount of $typeCount types on") { page = Page.QUOTES },
            Tile("⌕", "Browse & search", "Find any line or author") { browse = listOf(BrowsePage.SEARCH) },
            Tile("◈", "Subjects", "Love, time, solitude\u2026 across all authors") { page = Page.SUBJECTS },
            Tile("✦", "Themes", theme.label + if (theme.motionLabel != null && animate) " · moving" else "") { page = Page.THEMES },
            Tile("Aa", "Text style", "${quoteFont.label} · ${quoteSize.label}") { page = Page.STYLE },
            Tile("◷", "Schedule", "Every " + (INTERVALS.firstOrNull { it.first == interval }?.second?.lowercase() ?: "$interval min")) { page = Page.SCHEDULE },
            Tile("♥", "Favourites", if (favorites.isEmpty()) "Tap ♡ on a quote to save it" else "${favorites.size} saved" + if (favoritesOnly) " · only these" else "") { page = Page.FAVORITES },
            Tile("✎", "My quotes", if (custom.isEmpty()) "Add your own" else "${custom.size} added") { page = Page.MINE },
        )
        tiles.chunked(2).forEachIndexed { i, pair ->
            item(key = "tiles-$i") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { TileCard(it, Modifier.weight(1f)) }
                }
            }
        }
        item(key = "tile-about") {
            TileCard(
                Tile(
                    "ⓘ",
                    "About, sources & updates",
                    latest?.takeIf { it.build > currentBuild }?.let { "Update available: build ${it.build}" }
                        ?: "Where each quote comes from, and app updates",
                ) { page = Page.ABOUT },
                Modifier.fillMaxWidth(),
                wide = true,
            )
        }
    }

    fun LazyListScope.quotesPage() {
        item(key = "q-intro") {
            Column {
                Text(
                    "Choose which authors and themes appear. Tap a name to read its quotes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
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
            SettingsCard("Modern authors") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Authors still in copyright")
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
            val groupOn = inGroup.count { it in categories }
            item(key = "group-${group.name}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = query.isBlank() && group != QuoteGroup.MINE, onClickLabel = if (open) "Collapse" else "Expand") {
                            expandedGroups = if (group.name in expandedGroups) expandedGroups - group.name else expandedGroups + group.name
                        }
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(group.title, style = MaterialTheme.typography.titleMedium)
                        Text("$groupOn of ${inGroup.size} on", style = MaterialTheme.typography.bodySmall)
                    }
                    if (group != QuoteGroup.MINE) Text(if (open) "▾" else "▸", style = MaterialTheme.typography.titleLarge)
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
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable(enabled = countOf(category) > 0, onClickLabel = "Read ${category.label}") {
                                    browse = listOf(BrowsePage.category(category))
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(category.label)
                            Text(
                                "${countOf(category)} quotes · View ›",
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
    }

    fun LazyListScope.themesPage() {
        item(key = "t-app") {
            SettingsCard("App theme") {
                AppTheme.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            ThemePreview(option, selected = theme == option, modifier = Modifier.weight(1f)) {
                                prefs.theme = option
                                onTheme(option)
                                refreshNotification()
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                theme.motionLabel?.let { label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label)
                            Text(
                                if (theme == AppTheme.STARRY && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    "Needs Android 13 or newer; this phone shows the still painting."
                                } else {
                                    "Only moves while it's on screen, so it doesn't use battery in the background."
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
            }
        }
        item(key = "t-lock") {
            SettingsCard("Lock screen theme") {
                Text(
                    "The frame shows when the lock-screen notification is expanded.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (listOf<AppTheme?>(null) + AppTheme.entries).forEach { option ->
                        FilterChip(
                            selected = lockTheme == option,
                            onClick = {
                                prefs.lockTheme = option
                                lockTheme = option
                                refreshNotification()
                            },
                            label = { Text(option?.label ?: "Same as app") },
                        )
                    }
                }
            }
        }
        if (theme == AppTheme.AURORA || lockTheme == AppTheme.AURORA) {
            item(key = "t-aurora") {
                SettingsCard("Northern lights intensity") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "How bright and tall the curtains of light are, in the app and the live wallpaper.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${(auroraIntensity * 100).toInt()}%", modifier = Modifier.padding(start = 12.dp))
                    }
                    Slider(
                        value = auroraIntensity,
                        onValueChange = onAuroraIntensity,
                        onValueChangeFinished = { prefs.auroraIntensity = auroraIntensity },
                        valueRange = 0.5f..2f,
                    )
                }
            }
        }
        item(key = "t-opacity") {
            SettingsCard("Card opacity") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "How strong the card behind the quote is. Lower shows more of the painting, sky or page; " +
                            "0% leaves just the text.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(cardOpacity * 100).toInt()}%", modifier = Modifier.padding(start = 12.dp))
                }
                Slider(
                    value = cardOpacity,
                    onValueChange = onCardOpacity,
                    onValueChangeFinished = {
                        prefs.cardOpacity = cardOpacity
                        refreshNotification()
                    },
                    valueRange = 0f..1f,
                )
            }
        }
        item(key = "t-softness") {
            SettingsCard("Soft edge") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "0% is a solid card with a clean edge. Higher fades the edges into the artwork, like a vignette. " +
                            "Applies to the lock-screen card (Android 12+), the live wallpaper and shared pictures. " +
                            "The lock-screen card has three steps of softness.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(cardSoftness * 100).toInt()}%", modifier = Modifier.padding(start = 12.dp))
                }
                Slider(
                    value = cardSoftness,
                    onValueChange = { cardSoftness = it },
                    onValueChangeFinished = {
                        prefs.cardSoftness = cardSoftness
                        refreshNotification()
                    },
                    valueRange = 0f..1f,
                )
            }
        }
        item(key = "t-wallpaper") {
            SettingsCard("Live wallpaper") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Set as wallpaper")
                        Text(
                            "The lock-screen theme's artwork with today's quote, on your lock and home screen. " +
                                "Classic uses Starry Night. Moving follows the switch above.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(onClick = { openWallpaperPicker(context) }, modifier = Modifier.padding(start = 12.dp)) { Text("Set") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show the quote on the wallpaper", modifier = Modifier.weight(1f))
                    Switch(
                        checked = wallpaperQuote,
                        onCheckedChange = {
                            wallpaperQuote = it
                            prefs.wallpaperShowQuote = it
                        },
                    )
                }
                if (wallpaperQuote) {
                    Text("Quote position", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.4f to "Higher", 0.55f to "Middle", 0.72f to "Lower").forEach { (pos, label) ->
                            FilterChip(
                                selected = kotlin.math.abs(wallpaperPosition - pos) < 0.01f,
                                onClick = {
                                    wallpaperPosition = pos
                                    prefs.wallpaperQuotePosition = pos
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text(
                        "Move it if it overlaps your lock-screen clock or notifications.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    fun LazyListScope.stylePage() {
        item(key = "s-preview") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val family = fontFamilyOf(quoteFont)
                    val textColor = quoteColor?.let { Color(it) } ?: Color.Unspecified
                    Text(
                        "“${current?.text ?: "The unexamined life is not worth living."}”",
                        fontFamily = family,
                        fontSize = quoteSize.sizeSp.sp,
                        color = textColor,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "— ${current?.author ?: "Socrates"}",
                        fontFamily = family,
                        fontSize = (quoteSize.sizeSp * 0.85f).sp,
                        color = textColor,
                    )
                }
            }
        }
        item(key = "s-font") {
            SettingsCard("Font") {
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
            }
        }
        item(key = "s-size") {
            SettingsCard("Size") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuoteSize.entries.forEach { size ->
                        FilterChip(
                            selected = quoteSize == size,
                            onClick = { chooseSize(size) },
                            label = { Text(size.label) },
                        )
                    }
                }
            }
        }
        item(key = "s-color") {
            SettingsCard("Colour") {
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
                    label = { Text("Custom colour (hex, e.g. FFC107)") },
                    isError = hex.isNotEmpty() && !HEX_COLOR.matches(hex),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "With the Classic lock-screen theme Android draws the notification background itself, " +
                        "following your light or dark mode, so pick a colour that stands out against it. " +
                        "\"System default\" always stays readable; themed lock screens use their own colours.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    fun LazyListScope.schedulePage() {
        item(key = "sch-interval") {
            SettingsCard("Change the quote every") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow repeats")
                        Text(
                            if (allowRepeats) {
                                "Any quote can come up again at any time (never twice in a row)."
                            } else {
                                "Quotes never repeat until every enabled quote has been shown."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = allowRepeats, onCheckedChange = {
                        allowRepeats = it
                        prefs.allowRepeats = it
                    })
                }
            }
        }
        item(key = "sch-notif") {
            SettingsCard("Lock screen") {
                Text(
                    "The quote is an ongoing notification. For it to appear on the lock screen, " +
                        "set Settings > Notifications > Notifications on lock screen to show " +
                        "content and keep silent notifications enabled. Pull the notification " +
                        "down to read long quotes in full.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }) { Text("Notification settings") }
            }
        }
    }

    fun LazyListScope.minePage() {
        item(key = "m-add") {
            SettingsCard("Add your own quote") {
                OutlinedTextField(
                    value = newText,
                    onValueChange = { newText = it },
                    label = { Text("Quote") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newAuthor,
                    onValueChange = { newAuthor = it },
                    label = { Text("Author (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = newText.isNotBlank(),
                    onClick = {
                        repo.addCustom(newText.trim(), newAuthor.trim().ifBlank { null })
                        custom = repo.custom()
                        newText = ""
                        newAuthor = ""
                    },
                ) { Text("Add") }
                Text(
                    "Your quotes join the rotation while \"My own quotes\" is on in Quotes & authors.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (custom.isEmpty()) {
            item(key = "m-empty") { Text("You haven't added any quotes yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(custom, key = { it.id }) { quote ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(quote.text)
                        quote.author?.let { Text("— $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    TextButton(onClick = {
                        repo.removeCustom(quote.id)
                        custom = repo.custom()
                    }) { Text("Delete") }
                }
            }
        }
    }

    fun LazyListScope.subjectsPage() {
        item(key = "s-intro") {
            Text(
                "Browse by what a quote is about, across every author. Each quote is tagged automatically from its " +
                    "words, so a few may sit under a subject loosely and some are untagged; every quote is still under " +
                    "its author.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(Subject.entries.filter { (subjectCounts[it.key] ?: 0) > 0 }, key = { "subject-" + it.key }) { subject ->
            Card(onClick = { browse = listOf(BrowsePage.subject(subject)) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(subject.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${subjectCounts[subject.key]} \u203A",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    fun LazyListScope.favoritesPage() {
        item(key = "f-only") {
            SettingsCard("Lock screen") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Only show favourites",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = favoritesOnly, onCheckedChange = { setFavoritesOnly(it) })
                }
                Text(
                    if (favoritesOnly && favorites.isEmpty()) {
                        "You have no favourites yet, so every enabled quote type is used for now."
                    } else {
                        "When on, the lock screen rotates only through the quotes saved here."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (favorites.isEmpty()) {
            item(key = "f-empty") {
                Text(
                    "No favourites yet. Tap ♡ next to a quote on the home screen, or \"♡ Favourite\" in Browse & search.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(favorites.asReversed(), key = { "fav-" + it.text.hashCode() + it.author }) { quote ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("“${quote.text}”")
                    quote.author?.let { label ->
                        val name = authorName(label)
                        Text(
                            "— $label",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (name != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (name != null) Modifier.clickable { browse = listOf(BrowsePage.author(name)) } else Modifier,
                        )
                    }
                    Row {
                        val compact = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        TextButton(onClick = {
                            current = repo.show(quote)
                            canGoBack = repo.hasPrevious()
                            if (enabled) QuoteNotifier.show(context, quote)
                            page = Page.HOME
                        }, contentPadding = compact) { Text("Show now") }
                        TextButton(onClick = { QuoteClipboard.copy(context, quote) }, contentPadding = compact) { Text("Copy") }
                        TextButton(onClick = { QuoteShare.share(context, quote) }, contentPadding = compact) { Text("Share") }
                        TextButton(onClick = { toggleFavorite(quote) }, contentPadding = compact) { Text("Remove") }
                    }
                }
            }
        }
    }

    fun LazyListScope.aboutPage() {
        item(key = "a-update") {
            SettingsCard("App updates") {
                Text("You have build $currentBuild.", style = MaterialTheme.typography.bodyMedium)
                val release = latest
                updateStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                if (release != null && release.build > currentBuild) {
                    val notes = release.notes
                    if (notes.isNotEmpty()) Text(notes, style = MaterialTheme.typography.bodySmall)
                }
                downloadProgress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (release != null && release.build > currentBuild) {
                        Button(onClick = { installUpdate(release) }, enabled = !updateBusy) {
                            Text(if (downloaded != null) "Install" else "Download & install")
                        }
                    }
                    OutlinedButton(onClick = { checkForUpdate() }, enabled = !updateBusy) { Text("Check for updates") }
                }
                if (updateFailed) {
                    // The browser can often reach GitHub when the app can't (it may use its own secure DNS).
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.APK_DOWNLOAD)))
                    }) { Text("Download in browser") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Check weekly")
                        Text(
                            "Sends a notification when a new build is on GitHub. Android always asks you to confirm an install.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = updateCheck, onCheckedChange = {
                        updateCheck = it
                        prefs.updateCheck = it
                        Updater.schedule(context, it)
                    })
                }
            }
        }
        item(key = "a-intro") {
            SettingsCard("How the quotes are chosen") {
                Text(
                    "Public-domain authors are cut word for word from the editions listed below, mostly from " +
                        "Project Gutenberg and the Internet Archive. Lines marked \"(tr. Claude)\" are new, plain " +
                        "translations from the original language. Authors still in copyright appear as short " +
                        "excerpts gathered from Goodreads and checked by hand; they can be switched off together " +
                        "in Quotes & authors.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(sources.size, key = { "src-$it" }) { i ->
            val (kind, text) = sources[i]
            when (kind) {
                0 -> Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                2 -> Text(text, style = MaterialTheme.typography.bodySmall)
                else -> Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (page != Page.HOME) header()
            when (page) {
                Page.HOME -> homePage()
                Page.QUOTES -> quotesPage()
                Page.THEMES -> themesPage()
                Page.STYLE -> stylePage()
                Page.SCHEDULE -> schedulePage()
                Page.FAVORITES -> favoritesPage()
                Page.SUBJECTS -> subjectsPage()
                Page.MINE -> minePage()
                Page.ABOUT -> aboutPage()
            }
        }
    }
}

/** The pages reachable from the home screen's tiles. */
private enum class Page(val title: String) {
    HOME(""),
    QUOTES("Quotes & authors"),
    THEMES("Themes"),
    STYLE("Text style"),
    SCHEDULE("Schedule"),
    FAVORITES("Favourites"),
    SUBJECTS("Subjects"),
    MINE("My quotes"),
    ABOUT("About & updates"),
}

private class Tile(val symbol: String, val title: String, val subtitle: String, val onClick: () -> Unit)

@Composable
private fun TileCard(tile: Tile, modifier: Modifier, wide: Boolean = false) {
    Card(onClick = tile.onClick, modifier = modifier.heightIn(min = if (wide) 64.dp else 104.dp)) {
        if (wide) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tile.symbol, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(tile.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(tile.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                Text(tile.symbol, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                Text(tile.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    tile.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

/** A small picture of each theme to tap. */
@Composable
private fun ThemePreview(option: AppTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(88.dp)
            .clip(shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = "Use ${option.label}", onClick = onClick),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val image = when (option) {
            AppTheme.STARRY -> R.drawable.starry_frame
            AppTheme.AURORA -> R.drawable.aurora_frame
            AppTheme.BOOK -> R.drawable.paper
            AppTheme.BUTTERFLY -> R.drawable.butterfly_frame
            AppTheme.CLASSIC -> null
        }
        if (image != null) {
            Image(painterResource(image), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE8E4F4), Color(0xFFCFD6EE)))))
        }
        Text(
            option.label,
            style = MaterialTheme.typography.labelSmall.copy(shadow = null),
            color = if (option == AppTheme.CLASSIC || option.isLight) Color(0xFF2E2012) else Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (option == AppTheme.CLASSIC || option.isLight) Color(0x66FFFFFF) else Color(0x88000000)
                )
                .padding(vertical = 4.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/** Opens Android's live-wallpaper preview with ours selected; the user confirms with "Set wallpaper". */
private fun openWallpaperPicker(context: Context) {
    val component = ComponentName(context, QuoteWallpaperService::class.java)
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    try {
        context.startActivity(direct)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "This phone doesn't support live wallpapers.", Toast.LENGTH_LONG).show()
        }
    }
}

/** SOURCES.md, flattened for the About page: 0 = heading, 1 = text, 2 = list entry. */
private fun readSources(context: Context): List<Pair<Int, String>> {
    fun clean(s: String) = s.replace("**", "").replace("`", "").replace("*", "").replace("_", " ").trim()
    val out = mutableListOf<Pair<Int, String>>()
    context.resources.openRawResource(R.raw.sources).bufferedReader().useLines { lines ->
        lines.map { it.trim() }.forEach { l ->
            when {
                l.isEmpty() -> out.add(-1 to "")
                l.matches(Regex("^\\|[\\s:|-]+\\|$")) -> Unit
                l.startsWith("#") -> out.add(0 to clean(l.trimStart('#')))
                l.startsWith("|") -> {
                    val cells = l.trim('|').split("|").map { clean(it) }.filter { it.isNotEmpty() }
                    if (cells.firstOrNull() != "Author") out.add(2 to cells.joinToString(" · "))
                }
                else -> {
                    // Join wrapped paragraph lines.
                    val last = out.lastOrNull()
                    if (last != null && last.first == 1) out[out.size - 1] = 1 to (last.second + " " + clean(l))
                    else out.add(1 to clean(l))
                }
            }
        }
    }
    return out.filter { it.first >= 0 }
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
