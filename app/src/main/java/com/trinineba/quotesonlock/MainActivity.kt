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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.annotation.DrawableRes
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
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
        // A random theme, if chosen: a new one each day, or each time the app starts.
        val themeBefore = prefs.theme
        if (savedInstanceState == null) when (prefs.randomTheme) {
            "open" -> prefs.shuffleTheme()
            "daily" -> {
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                if (prefs.randomThemeDay != today) {
                    prefs.randomThemeDay = today
                    prefs.shuffleTheme()
                }
            }
        }
        if (prefs.theme != themeBefore) {
            // The lock-screen card and widgets follow the app's theme unless they have their own.
            if (prefs.enabled) QuoteRepository(this, prefs).current()?.let { QuoteNotifier.show(this, it) }
            QuoteWidgets.refresh(this)
        }
        QuoteRepository(this, prefs).preload()
        if (prefs.enabled || QuoteWidgets.hasAny(this)) QuoteScheduler.ensureScheduled(this, prefs.intervalMinutes)
        Updater.schedule(this, prefs.updateCheck)
        val openUpdates = intent.getBooleanExtra(EXTRA_OPEN_UPDATES, false)
        setContent {
            var theme by remember { mutableStateOf(prefs.theme) }
            var animate by remember { mutableStateOf(prefs.animateBackground) }
            var cardOpacity by remember { mutableStateOf(prefs.cardOpacity) }
            var cardSoftness by remember { mutableStateOf(prefs.cardSoftness) }
            var motionSpeed by remember { mutableStateOf(prefs.motionSpeed) }
            var auroraIntensity by remember { mutableStateOf(prefs.auroraIntensity) }
            QuotesTheme(theme, animate, cardOpacity, auroraIntensity, cardSoftness, motionSpeed) {
                QuotesScreen(
                    prefs = prefs,
                    repo = QuoteRepository(this, prefs),
                    theme = theme,
                    animate = animate,
                    onTheme = { theme = it },
                    onAnimate = { animate = it },
                    cardOpacity = cardOpacity,
                    onCardOpacity = { cardOpacity = it },
                    cardSoftness = cardSoftness,
                    onCardSoftness = { cardSoftness = it },
                    motionSpeed = motionSpeed,
                    onMotionSpeed = { motionSpeed = it },
                    auroraIntensity = auroraIntensity,
                    onAuroraIntensity = { auroraIntensity = it },
                    openUpdates = openUpdates,
                )
                WordSheetHost()
            }
        }
    }
}

@Composable
private fun QuotesTheme(
    theme: AppTheme,
    animate: Boolean,
    cardOpacity: Float,
    auroraIntensity: Float,
    cardSoftness: Float,
    motionSpeed: Float,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = theme.colorScheme(cardOpacity) ?: when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, typography = theme.typography(Typography())) {
        CompositionLocalProvider(LocalMotionSpeed provides motionSpeed, LocalCardSoftness provides cardSoftness) {
            Box(Modifier.fillMaxSize()) {
                ThemedBackground(theme, animate, auroraIntensity)
                content()
            }
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
    cardSoftness: Float,
    onCardSoftness: (Float) -> Unit,
    motionSpeed: Float,
    onMotionSpeed: (Float) -> Unit,
    auroraIntensity: Float,
    onAuroraIntensity: (Float) -> Unit,
    openUpdates: Boolean = false,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var interval by remember { mutableStateOf(prefs.intervalMinutes) }
    var allowRepeats by remember { mutableStateOf(prefs.allowRepeats) }
    var swipeCard by remember { mutableStateOf(prefs.swipeCard) }
    var ideasMix by remember { mutableStateOf(prefs.ideasMix) }
    var tool by rememberSaveable { mutableStateOf<String?>(null) }
    var mythsMix by remember { mutableStateOf(prefs.mythsMix) }
    var quoteFont by remember { mutableStateOf(prefs.font) }
    var quoteSize by remember { mutableStateOf(prefs.size) }
    var quoteColor by remember { mutableStateOf(prefs.textColor) }
    var lockTheme by remember { mutableStateOf(prefs.lockTheme) }
    var hex by rememberSaveable { mutableStateOf("") }
    var categories by remember { mutableStateOf(prefs.enabledCategories) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyOn by rememberSaveable { mutableStateOf(false) }
    var sourceQuery by rememberSaveable { mutableStateOf("") }
    var openSources by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(emptySet<String>()) }
    var expandedGroups by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(setOf(QuoteGroup.THEME.name)) }
    var current by remember { mutableStateOf(repo.current()) }
    var canGoBack by remember { mutableStateOf(repo.hasPrevious()) }
    var custom by remember { mutableStateOf(repo.custom()) }
    var favorites by remember { mutableStateOf(repo.favorites()) }
    // Notes and collections on favourites.
    var favInfo by remember { mutableStateOf(repo.favoriteInfo()) }
    var collections by remember { mutableStateOf(repo.collections()) }
    var favFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var noteFor by remember { mutableStateOf<Quote?>(null) }
    var listsFor by remember { mutableStateOf<Quote?>(null) }
    // The journal: reflections on quotes, kept on the phone.
    var journal by remember { mutableStateOf(Journal.entries(prefs)) }
    var reflectOn by remember { mutableStateOf(prefs.reflectPrompt) }
    var writingAbout by remember { mutableStateOf<Quote?>(null) }
    var writingPair by remember { mutableStateOf<Pair<Quote, Quote>?>(null) }
    var editingEntry by remember { mutableStateOf<JournalEntry?>(null) }
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
    // The sources split at their headings: (heading, its lines).
    val sourceSections = remember(sources) {
        val out = mutableListOf<Pair<String, MutableList<Pair<Int, String>>>>()
        sources.forEach { (kind, text) ->
            if (kind == 0) out.add(text to mutableListOf()) else {
                if (out.isEmpty()) out.add("Sources" to mutableListOf())
                out.last().second.add(kind to text)
            }
        }
        out.filter { it.second.isNotEmpty() }.map { (title, lines) ->
            (if (title == "Sources") "Public-domain texts and translations" else title) to lines.toList()
        }
    }
    // Open pages of the quote browser, innermost last; empty means the main screen.
    var browse by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.listSaver<List<String>, String>(save = { it }, restore = { it })) { mutableStateOf(emptyList<String>()) }

    // The notification's "Next quote" button changes the quote while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        current = repo.current()
        canGoBack = repo.hasPrevious()
        favorites = repo.favorites(); favInfo = repo.favoriteInfo(); collections = repo.collections()
    }
    // Leaving the app: draw any look changes onto the home-screen widgets.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { QuoteWidgets.refresh(context) }

    var welcome by rememberSaveable { mutableStateOf(!prefs.welcomeDone) }
    var widgetTheme by remember { mutableStateOf(prefs.widgetTheme) }
    var lastBackup by remember { mutableStateOf(prefs.lastBackup) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    // The text of a backup whose quotes were restored, while asking whether to restore its settings too.
    var pendingSettings by remember { mutableStateOf<String?>(null) }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            backupStatus = try {
                withContext(Dispatchers.IO) {
                    val text = Backup.export(context)
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }
                prefs.lastBackup = System.currentTimeMillis()
                lastBackup = prefs.lastBackup
                "Backup saved."
            } catch (e: Exception) {
                "Couldn't save the backup: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                val result = Backup.restoreQuotes(context, text)
                favorites = repo.favorites(); favInfo = repo.favoriteInfo(); collections = repo.collections()
                custom = repo.custom()
                backupStatus = "Restored ${result.favoritesAdded} favourite" + (if (result.favoritesAdded == 1) "" else "s") +
                    " and ${result.customAdded} of your own quote" + (if (result.customAdded == 1) "" else "s") +
                    " (ones you already had were kept as they are)."
                if (result.hasSettings) pendingSettings = text
            } catch (e: Exception) {
                backupStatus = e.message ?: "Couldn't read that file."
            }
        }
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
        // Home-screen widgets keep changing on the schedule even with the lock screen off.
        if (!QuoteWidgets.hasAny(context)) QuoteScheduler.cancel(context)
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

    fun refreshFavorites() {
        favorites = repo.favorites()
        favInfo = repo.favoriteInfo()
        collections = repo.collections()
        if (favFilter != null && favFilter !in collections) favFilter = null
    }

    fun toggleFavorite(quote: Quote) {
        repo.toggleFavorite(quote)
        refreshFavorites()
    }

    writingPair?.let { (a, b) ->
        val prompt = "Alongside “${b.text.take(90).trimEnd()}${if (b.text.length > 90) "…" else ""}” (${authorName(b.byline) ?: b.byline.orEmpty()}): what do these two lines say to each other?"
        JournalDialog(
            quoteText = a.shownText,
            prompt = prompt,
            initial = "",
            onSave = { text -> Journal.add(prefs, a, prompt, text); journal = Journal.entries(prefs); writingPair = null },
            onDelete = null,
            onDismiss = { writingPair = null },
        )
    }
    writingAbout?.let { q ->
        val prompt = Journal.promptFor(q)
        JournalDialog(
            quoteText = q.shownText,
            prompt = prompt,
            initial = "",
            onSave = { text -> Journal.add(prefs, q, prompt, text); journal = Journal.entries(prefs); writingAbout = null },
            onDelete = null,
            onDismiss = { writingAbout = null },
        )
    }
    editingEntry?.let { e ->
        JournalDialog(
            quoteText = e.quote,
            prompt = e.prompt,
            initial = e.text,
            onSave = { text -> Journal.update(prefs, e.time, text); journal = Journal.entries(prefs); editingEntry = null },
            onDelete = { Journal.delete(prefs, e.time); journal = Journal.entries(prefs); editingEntry = null },
            onDismiss = { editingEntry = null },
        )
    }
    noteFor?.let { q ->
        FavoriteNoteDialog(
            quote = q,
            initial = favInfo[QuoteData.wordsKey(q.text)]?.note.orEmpty(),
            onSave = { text -> repo.setFavoriteNote(q, text); noteFor = null; refreshFavorites() },
            onDismiss = { noteFor = null },
        )
    }
    listsFor?.let { q ->
        FavoriteCollectionsDialog(
            all = collections,
            selected = favInfo[QuoteData.wordsKey(q.text)]?.collections.orEmpty(),
            onSave = { chosen, created ->
                created.forEach { repo.addCollection(it) }
                repo.setFavoriteCollections(q, chosen)
                listsFor = null
                refreshFavorites()
            },
            onDismiss = { listsFor = null },
        )
    }

    fun setFavoritesOnly(on: Boolean) {
        favoritesOnly = on
        prefs.favoritesOnly = on
        val shown = current
        if (on && (shown == null || favorites.none { it.text == shown.text })) nextQuote()
    }

    pendingSettings?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingSettings = null },
            title = { Text("Restore settings too?") },
            text = {
                Text(
                    "The backup also has settings: which authors are on, the theme and lock-screen look, the schedule " +
                        "and text style. Replace your current settings with them?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingSettings = null
                    Backup.restoreSettings(context, text)
                    // Reload every screen from the restored settings.
                    (context as? android.app.Activity)?.recreate()
                }) { Text("Replace settings") }
            },
            dismissButton = { TextButton(onClick = { pendingSettings = null }) { Text("Keep mine") } },
        )
    }

    if (welcome) {
        Scaffold { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "w-title") {
                    Text("Welcome to Quotes on Lock", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
                }
                item(key = "w-quote") {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("“Life is indeed a strange gift, and its privileges are most mysterious.”", fontFamily = fontFamilyOf(quoteFont), fontSize = (quoteSize.sizeSp + 1).sp)
                            Text(
                                "— William Hazlitt",
                                fontFamily = fontFamilyOf(quoteFont),
                                fontSize = (quoteSize.sizeSp * 0.85f).sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                item(key = "w-lock") {
                    WelcomeStep(R.drawable.ic_lock, "Quotes on your lock screen", "A quiet card that changes on your schedule. Android needs your permission to show it.") {
                        if (enabled) {
                            Text("✓ Showing on your lock screen", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7FD69B))
                        } else {
                            OutlinedButton(onClick = { onToggle(true) }) { Text("Allow") }
                        }
                    }
                }
                item(key = "w-who") {
                    val quoteCount = remember { repo.bundled().size }
                    val modernOn = Category.MODERN.all { it in categories }
                    WelcomeStep(
                        R.drawable.ic_format_quote,
                        "Choose who you read",
                        "%,d quotes from writers, poets, thinkers and scriptures. Turn any of them on or off later in Quotes & authors.".format(quoteCount),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Modern authors", style = MaterialTheme.typography.bodyMedium)
                                Text("Short excerpts from writers still in copyright", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = modernOn, onCheckedChange = { setCategories(Category.MODERN, it) })
                        }
                    }
                }
                item(key = "w-look") {
                    WelcomeStep(R.drawable.ic_palette, "Pick a look", "For the app, the lock-screen card and the widgets. More choices in Appearance.") {
                        AppTheme.entries.chunked(5).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { option ->
                                    LockThemeTile(option, selected = theme == option, modifier = Modifier.weight(1f)) {
                                        prefs.theme = option
                                        onTheme(option)
                                        refreshNotification()
                                    }
                                }
                                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Text(theme.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                item(key = "w-updates") {
                    WelcomeStep(
                        R.drawable.ic_info,
                        "Updates come from GitHub",
                        "About & updates finds new builds. The first time you install one, Android will ask you to allow " +
                            "\"Install unknown apps\" for Quotes on Lock.",
                    ) {}
                }
                item(key = "w-start") {
                    Button(
                        onClick = {
                            prefs.welcomeDone = true
                            welcome = false
                            if (current == null) nextQuote()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Start reading") }
                }
            }
        }
        return
    }

    // The toolbox's tools open full screen, over the page.
    tool?.let { t ->
        when (t) {
            "breathe" -> BreatheScreen(repo) { tool = null }
            "ground" -> GroundScreen(onBack = { tool = null }, onBreathe = { tool = "breathe" })
            "reframe" -> ReframeScreen(prefs) { tool = null }
            "sounds" -> SoundsScreen(prefs) { tool = null }
            "lifeline" -> LifelineScreen(prefs, repo) { tool = null; favorites = repo.favorites(); favInfo = repo.favoriteInfo(); collections = repo.collections() }
            else -> tool = null
        }
        return
    }

    when (browse.lastOrNull()) {
        "dialogues" -> {
            DialoguesScreen(onBack = { browse = browse.dropLast(1) }, onOpenAuthor = { browse = browse + BrowsePage.author(it) })
            return
        }
        "timeline" -> {
            TimelineScreen(onBack = { browse = browse.dropLast(1) }, onOpenAuthor = { browse = browse + BrowsePage.author(it) })
            return
        }
        "themes" -> {
            ThemesScreen(onBack = { browse = browse.dropLast(1) }, onOpenAuthor = { browse = browse + BrowsePage.author(it) })
            return
        }
    }
    browse.lastOrNull()?.takeIf { it == "influences" || it.startsWith("influences:") }?.let { route ->
        InfluencesScreen(
            start = route.substringAfter(':', "").toIntOrNull(),
            onBack = { browse = browse.dropLast(1) },
            onOpenAuthor = { browse = browse + BrowsePage.author(it) },
        )
        return
    }
    browse.lastOrNull()?.let { bp ->
        QuoteBrowser(
            page = bp,
            repo = repo,
            onBack = {
                browse = browse.dropLast(1)
                favorites = repo.favorites(); favInfo = repo.favoriteInfo(); collections = repo.collections()
            },
            onShow = { quote ->
                current = repo.show(quote)
                canGoBack = repo.hasPrevious()
                if (enabled) QuoteNotifier.show(context, quote)
                browse = emptyList()
                favorites = repo.favorites(); favInfo = repo.favoriteInfo(); collections = repo.collections()
                page = Page.HOME
            },
            onOpenAuthor = { browse = browse + BrowsePage.author(it) },
            onOpenSubject = { browse = browse + BrowsePage.subject(it) },
            onOpenPage = { browse = browse + it },
            onSwitchPage = { browse = browse.dropLast(1) + it },
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
                ) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null) }
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
            Text("Quotes on Lock", style = MaterialTheme.typography.headlineMedium)
        }
        item(key = "home-lock") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(start = 16.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    if (enabled) "Showing on the lock screen" else "Not on the lock screen",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                Switch(checked = enabled, onCheckedChange = { onToggle(it) })
            }
        }
        item(key = "home-quote") {
            // Swiping the card: it follows the finger, and past the threshold flies off and the next
            // (left) or previous (right) quote slides in from the other side.
            val swipe = remember { Animatable(0f) }
            // A short drag (about a fingertip) or a quick flick of any length changes the quote.
            val threshold = with(LocalDensity.current) { 36.dp.toPx() }
            val flick = with(LocalDensity.current) { 350.dp.toPx() } // per second
            val swipeModifier = if (!swipeCard) Modifier else Modifier.pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        // Nothing to go back to: the card only gives a little, then springs back.
                        val step = if (amount > 0 && swipe.value >= 0f && !canGoBack) amount * 0.25f else amount
                        scope.launch { swipe.snapTo(swipe.value + step) }
                    },
                    onDragEnd = {
                        val width = size.width.toFloat()
                        val velocity = tracker.calculateVelocity().x
                        scope.launch {
                            val offset = swipe.value
                            val left = offset < -threshold || (velocity < -flick && offset < 0f)
                            val right = offset > threshold || (velocity > flick && offset > 0f)
                            when {
                                left -> {
                                    swipe.animateTo(-width, tween(150))
                                    nextQuote()
                                    swipe.snapTo(width * 0.5f)
                                    swipe.animateTo(0f, tween(220))
                                }
                                right && canGoBack -> {
                                    swipe.animateTo(width, tween(150))
                                    previousQuote()
                                    swipe.snapTo(-width * 0.5f)
                                    swipe.animateTo(0f, tween(220))
                                }
                                else -> swipe.animateTo(0f, spring())
                            }
                        }
                    },
                    onDragCancel = { scope.launch { swipe.animateTo(0f, spring()) } },
                )
            }
            SoftCard(
                modifier = Modifier.fillMaxWidth()
                    .then(swipeModifier)
                    .graphicsLayer {
                        translationX = swipe.value
                        alpha = 1f - (kotlin.math.abs(swipe.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.7f)
                    }
                    .then(
                    // The thin outline suits a solid card; a soft edge fades out, so it has none.
                if (theme == AppTheme.CLASSIC || cardSoftness >= 0.03f) Modifier
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
                        quote.title?.let { title ->
                            Column {
                                // Tap the label to see every idea or myth of this type.
                                val kind = quote.category
                                Text(
                                    ((kind?.group?.title ?: "Great ideas") + " · " + (kind?.label ?: "") + if (kind != null) " ›" else "").uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.2.sp,
                                    modifier = if (kind != null) {
                                        Modifier.clickable(onClickLabel = "All ${kind.label}") { browse = listOf(BrowsePage.category(kind)) }
                                    } else Modifier,
                                )
                                Text(title, fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = (quoteSize.sizeSp + 5).sp, color = textColor)
                            }
                        }
                        Text(
                            readingText(quote),
                            fontFamily = if (quote.explained) null else family,
                            fontSize = (quoteSize.sizeSp + if (quote.explained) 0 else 2).sp,
                            color = textColor,
                        )
                        // The byline, with the read-aloud button at its end.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                quote.byline?.let { author ->
                                    val name = authorName(author)
                                    Text(
                                        if (name != null) "— $author ›" else "— $author",
                                        fontFamily = family,
                                        fontSize = (quoteSize.sizeSp * 0.9f).sp,
                                        color = if (quoteColor == null && theme != AppTheme.CLASSIC) MaterialTheme.colorScheme.primary else textColor,
                                        // Tap the author to see all of their quotes.
                                        modifier = if (name != null) {
                                            Modifier.clickable(onClickLabel = "All quotes by $name") { browse = listOf(BrowsePage.author(name)) }
                                        } else Modifier,
                                    )
                                }
                            }
                            ReadAloudButton(quote)
                        }
                        quote.brief?.let { brief ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            Text("IN BRIEF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp)
                            Text(brief, style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                        quote.more?.let { MoreAbout(quote.text, it, textColor) }
                        if (quote.isIdea) {
                            Text(
                                if (quote.explained) "Written for the app" else "Explanation written for the app",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    current?.let { q -> EchoNote(q, favorites) { a, b -> writingPair = a to b } }
                    val favorite = quote != null && favorites.any { it.text == quote.text }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        QuoteAction(R.drawable.ic_arrow_back, "Previous", enabled = canGoBack) { previousQuote() }
                        QuoteAction(R.drawable.ic_arrow_forward, "Next") { nextQuote() }
                        ContextAction(quote)
                        MoreLikeThisAction(quote) { refreshFavorites() }
                        ShareAction(quote)
                        QuoteAction(
                            if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                            if (favorite) "Saved" else "Save",
                            enabled = quote != null,
                            tint = if (favorite) Color(0xFFE0566B) else null,
                        ) { quote?.let { toggleFavorite(it) } }
                    }
                }
            }
        }
        if (reflectOn) current?.let { q ->
            item(key = "home-reflect") {
                // A quiet question to think with; writing is optional and stays on the phone.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Journal.promptFor(q),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { writingAbout = q }) { Text("Reflect") }
                }
            }
        }
        val typeCount = Category.entries.count { it != Category.CUSTOM && countOf(it) > 0 }
        val onCount = Category.entries.count { it in categories && countOf(it) > 0 }
        val tiles = listOf(
            Tile(R.drawable.ic_format_quote, "Quotes & authors", "$onCount of $typeCount on") { page = Page.QUOTES },
            Tile(R.drawable.ic_explore, "Explore", "Search, subjects, authors A–Z") { browse = listOf(BrowsePage.SEARCH) },
            Tile(R.drawable.ic_palette, "Appearance", theme.label + " · " + quoteFont.label) { page = Page.THEMES },
            Tile(R.drawable.ic_schedule, "Schedule", "Every " + (INTERVALS.firstOrNull { it.first == interval }?.second?.lowercase() ?: "$interval min")) { page = Page.SCHEDULE },
            Tile(R.drawable.ic_favorite_border, "Favourites", if (favorites.isEmpty()) "Tap the heart on a quote" else "${favorites.size} saved" + if (favoritesOnly) " · only these" else "") { page = Page.FAVORITES },
            Tile(R.drawable.ic_edit, "My quotes", if (custom.isEmpty()) "Add your own" else "${custom.size} added") { page = Page.MINE },
        )
        tiles.chunked(2).forEachIndexed { i, pair ->
            item(key = "tiles-$i") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { TileCard(it, Modifier.weight(1f)) }
                }
            }
        }
        item(key = "tile-journal") {
            TileCard(
                Tile(R.drawable.ic_edit_note, "Journal", if (journal.isEmpty()) "Your reflections on quotes, kept on this phone" else "${journal.size} reflection" + if (journal.size == 1) "" else "s") { page = Page.JOURNAL },
                Modifier.fillMaxWidth(),
                wide = true,
            )
        }
        item(key = "tile-toolbox") {
            // Low down and quiet, on purpose: it's there when needed, not in the way.
            TileCard(
                Tile(R.drawable.ic_spa, "When it's hard", "Comfort, calm and support") { page = Page.TOOLBOX },
                Modifier.fillMaxWidth(),
                wide = true,
            )
        }
        item(key = "tile-about") {
            TileCard(
                Tile(
                    R.drawable.ic_info,
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
        item(key = "q-mix") {
            // How often great ideas and myths come up, compared with their natural share of the pool.
            val (ideasShare, mythsShare) = remember(categories, custom.size) {
                val counts = repo.bundled().groupingBy { it.category }.eachCount()
                fun count(c: Category) = if (c == Category.CUSTOM) custom.size else counts[c] ?: 0
                val onTotal = Category.entries.filter { it in categories }.sumOf { count(it) }.coerceAtLeast(1)
                fun share(group: QuoteGroup): Int {
                    val n = Category.entries.filter { it.group == group && it in categories }.sumOf { count(it) }
                    return if (n == 0) 0 else (onTotal / n).coerceAtLeast(1)
                }
                share(QuoteGroup.IDEAS) to share(QuoteGroup.MYTHS)
            }
            if (ideasShare > 0 || mythsShare > 0) {
                SettingsCard("Mix") {
                    Text(
                        "How often great ideas and myths come up on the lock screen and in the app. " +
                            "\"Usual\" gives them their share of everything switched on.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    listOf(
                        Triple("Great ideas", ideasShare, ideasMix),
                        Triple("Mythology", mythsShare, mythsMix),
                    ).forEach { (label, natural, value) ->
                        if (natural == 0) return@forEach
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(0, 20, 10, 5, 3).forEach { n ->
                                FilterChip(
                                    selected = value == n,
                                    onClick = {
                                        if (label == "Great ideas") { ideasMix = n; prefs.ideasMix = n } else { mythsMix = n; prefs.mythsMix = n }
                                    },
                                    label = { Text(if (n == 0) "Usual (1 in $natural)" else "1 in $n") },
                                )
                            }
                        }
                    }
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search authors and types") },
                )
                FilterChip(
                    selected = onlyOn,
                    onClick = { onlyOn = !onlyOn },
                    label = { Text("Only the ones that are on") },
                )
            }
        }
        QuoteGroup.entries.forEach { group ->
            // Authors A–Z (ignoring a leading "The"); the themes keep their own order.
            val inGroup = Category.entries
                .filter { it.group == group && (it == Category.CUSTOM || countOf(it) > 0) }
                .let { list -> if (group == QuoteGroup.THEME || group == QuoteGroup.MINE) list else list.sortedBy { it.label.removePrefix("The ").lowercase() } }
            val shown = inGroup.filter {
                (query.isBlank() || it.label.contains(query.trim(), ignoreCase = true)) && (!onlyOn || it in categories)
            }
            if (shown.isEmpty()) return@forEach
            val open = query.isNotBlank() || onlyOn || group == QuoteGroup.MINE || group.name in expandedGroups
            val groupOn = inGroup.count { it in categories }
            item(key = "group-${group.name}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = query.isBlank() && !onlyOn && group != QuoteGroup.MINE, onClickLabel = if (open) "Collapse" else "Expand") {
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

    /** Appearance: a live preview, then theme, card, text, lock screen and wallpaper. */
    fun LazyListScope.themesPage() {
        item(key = "ap-preview") {
            // Every card in the app follows these settings; this one shows the effect right away.
            SoftCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val family = fontFamilyOf(quoteFont)
                    val textColor = quoteColor?.let { Color(it) } ?: Color.Unspecified
                    Text("Preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "“${current?.text ?: "The unexamined life is not worth living."}”",
                        fontFamily = family,
                        fontSize = quoteSize.sizeSp.sp,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "— ${current?.author ?: "Socrates"}",
                        fontFamily = family,
                        fontSize = (quoteSize.sizeSp * 0.85f).sp,
                        color = if (quoteColor == null && theme != AppTheme.CLASSIC) MaterialTheme.colorScheme.primary else textColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        item(key = "ap-theme") {
            SettingsCard("Theme") {
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
                var randomTheme by remember { mutableStateOf(prefs.randomTheme) }
                Text("Random theme", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("off" to "Off", "daily" to "Each day", "open" to "Each time I open the app").forEach { (key, label) ->
                        FilterChip(
                            selected = randomTheme == key,
                            onClick = {
                                randomTheme = key
                                prefs.randomTheme = key
                                if (key == "daily") prefs.randomThemeDay = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                            },
                            label = { Text(label) },
                        )
                    }
                    TextButton(onClick = {
                        onTheme(prefs.shuffleTheme())
                        refreshNotification()
                    }) { Text("Surprise me") }
                }
                theme.motionLabel?.let { label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label)
                            Text(
                                if (theme in listOf(AppTheme.STARRY, AppTheme.WATER, AppTheme.RAIN, AppTheme.RAIN_NIGHT) &&
                                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                                ) {
                                    "Needs Android 13 or newer; this phone shows a still picture."
                                } else {
                                    "Only moves while it's on screen."
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
                    if (animate) {
                        LabeledSlider(
                            "Speed",
                            String.format(java.util.Locale.US, "%.2f", motionSpeed).trimEnd('0').trimEnd('.') + "\u00D7",
                            motionSpeed, 0.25f..2f, onMotionSpeed,
                        ) { prefs.motionSpeed = motionSpeed }
                    }
                }
                if (theme == AppTheme.AURORA || lockTheme == AppTheme.AURORA) {
                    LabeledSlider("Lights", "${(auroraIntensity * 100).toInt()}%", auroraIntensity, 0.5f..2f, onAuroraIntensity) {
                        prefs.auroraIntensity = auroraIntensity
                    }
                }
            }
        }
        item(key = "ap-card") {
            SettingsCard("Card") {
                LabeledSlider("Opacity", "${(cardOpacity * 100).toInt()}%", cardOpacity, 0f..1f, onCardOpacity) {
                    prefs.cardOpacity = cardOpacity
                    refreshNotification()
                }
                LabeledSlider("Soft edge", "${(cardSoftness * 100).toInt()}%", cardSoftness, 0f..1f, onCardSoftness) {
                    prefs.cardSoftness = cardSoftness
                    refreshNotification()
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Swipe the card to change quotes")
                        Text(
                            "On the home screen of the app: swipe left for the next quote, right for the previous one. The buttons stay.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = swipeCard, onCheckedChange = {
                        swipeCard = it
                        prefs.swipeCard = it
                    })
                }
                Text(
                    "Opacity is how strong the card behind the text is; soft edge fades its edges into the artwork. " +
                        "They apply in the app, on the lock-screen card (soft edge on Android 12+, in three steps), " +
                        "the live wallpaper and shared pictures.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item(key = "ap-voice") {
            SettingsCard("Read aloud") {
                ReadAloudSettings(prefs) { refreshNotification() }
            }
        }
        item(key = "ap-text") {
            SettingsCard("Text") {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuoteSize.entries.forEach { size ->
                        FilterChip(
                            selected = quoteSize == size,
                            onClick = { chooseSize(size) },
                            label = { Text(size.label) },
                        )
                    }
                }
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
                    "With the Classic lock-screen theme Android draws the notification background itself, so pick a " +
                        "colour that stands out against it; \"System default\" always stays readable.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item(key = "ap-lock") {
            SettingsCard("Lock screen") {
                Text(
                    "The theme for the lock-screen card (its frame shows when the notification is expanded) and the live wallpaper.",
                    style = MaterialTheme.typography.bodySmall,
                )
                (listOf<AppTheme?>(null) + AppTheme.entries).chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { option ->
                            LockThemeTile(option, selected = lockTheme == option, modifier = Modifier.weight(1f)) {
                                prefs.lockTheme = option
                                lockTheme = option
                                refreshNotification()
                            }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text(
                    "Now: " + (lockTheme?.label ?: "same as the app (${theme.label})"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item(key = "ap-widget") {
            SettingsCard("Home-screen widget") {
                Text(
                    "Today's quote on your home screen: large (with a heart and next) or small (one or two lines). " +
                        "Tap a widget's quote to open the app.",
                    style = MaterialTheme.typography.bodySmall,
                )
                (listOf<AppTheme?>(null) + AppTheme.entries).chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { option ->
                            LockThemeTile(option, selected = widgetTheme == option, modifier = Modifier.weight(1f)) {
                                prefs.widgetTheme = option
                                widgetTheme = option
                                QuoteWidgets.refresh(context)
                            }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text(
                    "Now: " + (widgetTheme?.label ?: "same as the app (${theme.label})") + ". Moving themes show a still picture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "Add large", true to "Add small").forEach { (small, label) ->
                        OutlinedButton(onClick = {
                            if (!QuoteWidgets.requestPin(context, small)) {
                                Toast.makeText(
                                    context,
                                    "Long-press an empty spot on your home screen, tap Widgets, and find Quotes on Lock.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }) { Text(label) }
                    }
                }
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
                                if (enabled || QuoteWidgets.hasAny(context)) QuoteScheduler.schedule(context, minutes)
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
        item(key = "sch-daily") {
            var daily by remember { mutableStateOf(prefs.dailyEnabled) }
            var hour by remember { mutableIntStateOf(prefs.dailyHour) }
            var minute by remember { mutableIntStateOf(prefs.dailyMinute) }
            // Its own permission prompt: granting it here must not switch on the lock-screen card.
            val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            SettingsCard("Today's quote") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("A quote each day")
                        Text(
                            "One gentle notification a day with a quote from the types you have on, separate from the lock-screen card.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = daily, onCheckedChange = { on ->
                        daily = on
                        prefs.dailyEnabled = on
                        DailyQuote.schedule(context)
                        if (on && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
                }
                if (daily) {
                    OutlinedButton(onClick = {
                        android.app.TimePickerDialog(context, { _, h, m ->
                            hour = h; minute = m
                            prefs.dailyHour = h; prefs.dailyMinute = m
                            DailyQuote.schedule(context)
                        }, hour, minute, android.text.format.DateFormat.is24HourFormat(context)).show()
                    }) {
                        val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, hour); set(java.util.Calendar.MINUTE, minute) }
                        Text("At " + android.text.format.DateFormat.getTimeFormat(context).format(cal.time))
                    }
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
            SoftCard(Modifier.fillMaxWidth()) {
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
        if (favorites.isNotEmpty()) {
            item(key = "f-collections") {
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = favFilter == null, onClick = { favFilter = null }, label = { Text("All (${favorites.size})") })
                    collections.forEach { name ->
                        val n = favInfo.values.count { name in it.collections }
                        FilterChip(
                            selected = favFilter == name,
                            onClick = { favFilter = if (favFilter == name) null else name },
                            label = { Text("$name ($n)") },
                        )
                    }
                }
                if (favFilter != null) {
                    TextButton(onClick = {
                        repo.deleteCollection(favFilter!!)
                        favFilter = null
                        refreshFavorites()
                    }) { Text("Delete this collection (its quotes stay in favourites)") }
                }
            }
        }
        val shownFavorites = favorites.asReversed().filter { q ->
            favFilter == null || favInfo[QuoteData.wordsKey(q.text)]?.collections?.contains(favFilter) == true
        }
        items(shownFavorites, key = { "fav-" + it.text.hashCode() + it.author }) { quote ->
            val info = favInfo[QuoteData.wordsKey(quote.text)]
            var similar by remember(quote.text) { mutableStateOf(false) }
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    quote.title?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                    Text(readingText(quote))
                    quote.byline?.let { label ->
                        val name = authorName(label)
                        Text(
                            "— $label",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (name != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (name != null) Modifier.clickable { browse = listOf(BrowsePage.author(name)) } else Modifier,
                        )
                    }
                    info?.note?.takeIf { it.isNotBlank() }?.let { note ->
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp).clickable(onClickLabel = "Edit your note") { noteFor = quote },
                        )
                    }
                    info?.collections?.takeIf { it.isNotEmpty() }?.let { lists ->
                        Text(lists.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        ListAction(R.drawable.ic_edit_note, if (info?.note.isNullOrBlank()) "Add a note" else "Edit your note") { noteFor = quote }
                        ListAction(R.drawable.ic_label, "Collections") { listsFor = quote }
                        ListAction(R.drawable.ic_lock, "Show on the lock screen now") {
                            current = repo.show(quote)
                            canGoBack = repo.hasPrevious()
                            if (enabled) QuoteNotifier.show(context, quote)
                            page = Page.HOME
                        }
                        ReadAloudButton(quote)
                        ContextListAction(quote)
                        MoreLikeThisListAction(quote, similar) { similar = !similar }
                        ShareListAction(quote)
                        ListAction(R.drawable.ic_favorite, "Remove from favourites", tint = Color(0xFFE0566B)) { toggleFavorite(quote) }
                    }
                    if (similar) MoreLikeThisRow(quote) { refreshFavorites() }
                }
            }
        }
    }

    fun LazyListScope.journalPage() {
        item(key = "j-settings") {
            SettingsCard("Reflection") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("A question under the quote")
                        Text(
                            "A short question to think with appears under the home-screen quote. Tap Reflect to write; it is never required.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = reflectOn, onCheckedChange = { reflectOn = it; prefs.reflectPrompt = it })
                }
                Text(
                    "Your journal is kept only on this phone. It is included when you save a backup in About & updates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (journal.isEmpty()) {
            item(key = "j-empty") {
                Text(
                    "No reflections yet. Tap Reflect under a quote on the home screen to write a few lines about it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(journal, key = { "j-" + it.time }) { e ->
            SoftCard(Modifier.fillMaxWidth(), onClick = { editingEntry = e }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(java.util.Date(e.time)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "\u201C" + e.quote.take(180) + (if (e.quote.length > 180) "…" else "") + "\u201D" + (e.author?.let { " — " + authorName(it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(e.prompt, style = MaterialTheme.typography.labelLarge)
                    Text(e.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    fun LazyListScope.toolboxPage() {
        item(key = "tb-intro") {
            Text("Take your time. Nothing here is saved unless you choose to.", style = MaterialTheme.typography.bodyMedium)
        }
        item(key = "tb-feel") {
            SettingsCard("How are you feeling?") {
                FeelingChips { browse = listOf(BrowsePage.feeling(it)) }
                Text("Opens gentle quotes, ideas and myths for that feeling.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item(key = "tb-breathe") { ToolRow(R.drawable.ic_air, "Breathe", "A calm circle to breathe with \u00B7 1\u20135 min") { tool = "breathe" } }
        item(key = "tb-sounds") { ToolRow(R.drawable.ic_graphic_eq, "Calming sounds", "Rain, waves, a stream, birdsong \u00B7 sleep timer") { tool = "sounds" } }
        item(key = "tb-ground") { ToolRow(R.drawable.ic_visibility, "Ground yourself", "5 things you see, 4 you can touch\u2026") { tool = "ground" } }
        item(key = "tb-reframe") { ToolRow(R.drawable.ic_psychology, "Look at a thought again", "What would you tell a friend?") { tool = "reframe" } }
        item(key = "tb-lifeline") { ToolRow(R.drawable.ic_volunteer_activism, "My lifeline", "A letter to yourself, kept thoughts, what helps") { tool = "lifeline" } }
        item(key = "tb-note") {
            Text(
                "These are gentle companions, not a replacement for care from people who know you or from a professional.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        item(key = "a-backup") {
            SettingsCard("Backup & restore") {
                Text(
                    "${favorites.size} favourite" + (if (favorites.size == 1) "" else "s") + ", ${custom.size} of your own quote" +
                        (if (custom.size == 1) "" else "s") + ", and your settings (authors, theme, schedule, text style).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                        saveBackup.launch("quotes-on-lock-backup-$day.json")
                    }) { Text("Save backup file") }
                    OutlinedButton(onClick = { openBackup.launch(arrayOf("*/*")) }) { Text("Restore…") }
                }
                backupStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                Text(
                    "Saves one small file wherever you choose (Downloads, Drive…). Restoring adds favourites and your " +
                        "quotes you don't already have, then asks before replacing settings.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (lastBackup > 0) {
                    Text(
                        "Last backup: " + android.text.format.DateUtils.getRelativeTimeSpanString(
                            lastBackup, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item(key = "a-feedback") {
            SettingsCard("Feedback") {
                Text("Found a typo in a quote, or have an idea?", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FEEDBACK_URL)))
                    }) { Text("Send feedback") }
                    TextButton(onClick = {
                        prefs.welcomeDone = false
                        welcome = true
                    }) { Text("Show the welcome") }
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
        item(key = "src-search") {
            OutlinedTextField(
                value = sourceQuery,
                onValueChange = { sourceQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search the sources (an author, a book, a translator)") },
            )
        }
        val q = sourceQuery.trim()
        sourceSections.forEachIndexed { si, (title, entries) ->
            val matching = if (q.isEmpty()) entries else entries.filter { it.second.contains(q, ignoreCase = true) }
            if (matching.isEmpty()) return@forEachIndexed
            val open = q.isNotEmpty() || title in openSources
            item(key = "src-h-$si") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = q.isEmpty(), onClickLabel = if (open) "Collapse" else "Expand") {
                            openSources = if (title in openSources) openSources - title else openSources + title
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text("${matching.size} " + if (matching.size == 1) "entry" else "entries", style = MaterialTheme.typography.bodySmall)
                    }
                    if (q.isEmpty()) Text(if (open) "▾" else "▸", style = MaterialTheme.typography.titleLarge)
                }
            }
            if (open) {
                items(matching.size, key = { "src-$si-$it" }) { i ->
                    val (kind, text) = matching[i]
                    Text(
                        text,
                        style = if (kind == 2) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
    }

    // Each page opens at its top (About starts at the update card, not wherever the home screen was scrolled to).
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(page) { listState.scrollToItem(0) }
    Scaffold { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (page != Page.HOME) header()
            when (page) {
                Page.HOME -> homePage()
                Page.QUOTES -> quotesPage()
                Page.THEMES -> themesPage()
                Page.SCHEDULE -> schedulePage()
                Page.FAVORITES -> favoritesPage()
                Page.MINE -> minePage()
                Page.TOOLBOX -> toolboxPage()
                Page.JOURNAL -> journalPage()
                Page.ABOUT -> aboutPage()
            }
        }
    }
}

/** The pages reachable from the home screen's tiles. */
private enum class Page(val title: String) {
    HOME(""),
    QUOTES("Quotes & authors"),
    THEMES("Appearance"),
    SCHEDULE("Schedule"),
    FAVORITES("Favourites"),
    MINE("My quotes"),
    TOOLBOX("When it's hard"),
    JOURNAL("Journal"),
    ABOUT("About & updates"),
}
