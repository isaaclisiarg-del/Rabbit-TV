package com.luncher.launcher.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.apps.ArrangedApps
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.LaunchableApp
import com.luncher.domain.apps.homeApps
import com.luncher.launcher.settings.SettingsActivity
import java.util.Locale

/**
 * The Rabbit TV home surface: a three-entry dock, favorite app tiles and a second view for all TV
 * apps. OK opens favorites on Home and toggles favorite state on Apps; a long press of OK on Home
 * starts [ArrangeMode], where the user moves and removes favorites.
 *
 * How it looks follows the user's [Appearance] while it's shown: each part goes to the view that
 * draws it (the background to the window, [HomeWallpaper]), which compares it with what it has, so
 * an unchanged part costs nothing.
 */
class HomeActivity : Activity() {

    private enum class Page { HOME, APPS }

    private val installedApps by lazy { graph.installedApps }
    private val arrangements by lazy { graph.appArrangements }
    private val images by lazy { graph.appImages }
    private val clock by lazy { graph.clock }
    private val appearanceSettings by lazy { graph.appearanceSettings }
    private lateinit var topBar: TopBarView
    private lateinit var tiles: AppTilesView
    private lateinit var clockView: ClockView
    private lateinit var empty: TextView
    private lateinit var arrange: ArrangeMode
    private lateinit var wallpaper: HomeWallpaper
    private lateinit var pageHint: TextView
    private lateinit var homeEntry: TextView
    private lateinit var appsEntry: TextView
    private lateinit var settingsEntry: TextView
    private var page = Page.HOME

    /** What the tiles show; null until the first [onResume]. */
    private var shown: List<InstalledApp>? = null

    /** The apps as last read, shown and hidden: where arrange mode starts from. */
    private var arranged: ArrangedApps? = null

    private val showAppearance: () -> Unit = { show(appearanceSettings.read()) }

    // Hidden or shown in the settings, over the home screen: shown at once. Not while arranging,
    // whose own saves these are, with the tiles showing them already.
    private val showArrangement: () -> Unit = { if (!arrange.active) refresh() }

    /** Apps installed, updated or removed while the user arranges apps, read when the mode ends. */
    private var appsChangedWhileArranging = false

    // Installed, updated or removed while shown: shown at once, or, while arranging, whose tiles
    // are the user's work in progress, once it ends.
    private val showAppChanges: () -> Unit = {
        if (arrange.active) appsChangedWhileArranging = true else refresh()
    }

    private val startArranging = View.OnLongClickListener {
        val arranged = arranged ?: return@OnLongClickListener false
        arrange.start(arranged, it as AppTileView)
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_activity)
        topBar = findViewById(R.id.home_top_bar)
        tiles = findViewById(R.id.home_apps)
        tiles.navigateLeft = { if (page == Page.HOME) homeEntry else appsEntry }
        empty = findViewById(R.id.home_empty)
        clockView = findViewById(R.id.home_clock)
        pageHint = findViewById(R.id.home_page_hint)
        homeEntry = findViewById(R.id.home_dock_home)
        appsEntry = findViewById(R.id.home_dock_apps)
        settingsEntry = findViewById(R.id.home_dock_settings)
        homeEntry.setOnClickListener { selectPage(Page.HOME) }
        appsEntry.setOnClickListener { selectPage(Page.APPS) }
        settingsEntry.setOnClickListener { openSettings() }
        setupDockEntry(homeEntry)
        setupDockEntry(appsEntry)
        setupDockEntry(settingsEntry)
        findViewById<View>(R.id.home_settings).visibility = View.GONE
        updateDockSelection()
        wallpaper = HomeWallpaper(this, graph.wallpaper)
        arrange = ArrangeMode(
            tiles,
            normalBar = listOf(clockView),
            title = findViewById(R.id.home_arrange_title),
            hint = findViewById(R.id.home_arrange_hint),
            images,
            arrangements,
            onEnd = ::arrangingEnded,
        )
        // A home activity must not finish on Back: Back only ends arrange mode. From Android 16
        // (API 36) on, Back no longer calls onBackPressed in apps targeting it, nor reaches them
        // as a key, and closes the activity unless a callback takes it; before that,
        // onBackPressed below does.
        if (Build.VERSION.SDK_INT >= 36) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                arrange.back()
            }
        }
    }

    // Visible from onStart to onStop; paused only, e.g. behind a dialog, it still shows.
    // The appearance, the arrangement and the installed apps, as the clock, only while visible: a
    // change made meanwhile (in the appearance editor, which covers the home screen whole) is
    // applied once, when it comes back. The apps are read again in onResume.
    override fun onStart() {
        super.onStart()
        clockView.start(clock)
        wallpaper.start()
        appearanceSettings.addListener(showAppearance)
        showAppearance()
        arrangements.addListener(showArrangement)
        installedApps.addListener(showAppChanges)
    }

    override fun onStop() {
        super.onStop()
        arrange.end()   // leaving the home screen (another app, the screen off) ends it
        clockView.stop()
        wallpaper.stop()
        appearanceSettings.removeListener(showAppearance)
        arrangements.removeListener(showArrangement)
        installedApps.removeListener(showAppChanges)
    }

    override fun onResume() {
        super.onResume()
        // Paused and resumed while arranging (a system dialog came and went): the tiles are the
        // user's work in progress, so the apps are read again only when the mode ends.
        if (!arrange.active) refresh()
    }

    // Home while the home screen is in front.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        arrange.end()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        arrange.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    /**
     * Reads the apps again: apps may have been installed or removed, or hidden or shown in the
     * settings, since the home screen was last shown.
     */
    private fun refresh() {
        val installed = installedApps.tvApps()
        var saved = arrangements.read()
        val order = saved.order
        if (order == null && saved.hidden.isEmpty()) {
            val candidates = installed.asSequence()
                .filter { app -> app.launchable.packageName != packageName && isInitialFavorite(app) }
                .sortedBy(::initialFavoriteOrder)
                .toList()
            val selected = candidates.map { it.launchable }.toSet()
            saved = AppArrangement(
                order = candidates.map { it.launchable },
                hidden = installed.asSequence()
                    .filter { it.launchable.packageName != packageName && it.launchable !in selected }
                    .map { it.launchable }
                    .toList(),
            )
            arrangements.save(saved)
        }
        if (order != null) {
            val accountedFor = (order + saved.hidden).toHashSet()
            val newApps = installed.asSequence()
                .filter { it.launchable.packageName != packageName && it.launchable !in accountedFor }
                .map { it.launchable }
                .toList()
            if (newApps.isNotEmpty()) {
                saved = saved.copy(hidden = saved.hidden + newApps)
                arrangements.save(saved)
            }
        }
        val arranged = homeApps(installed, packageName, saved, Locale.getDefault())
        this.arranged = arranged
        // Unchanged, as on most returns to the home screen: keep the tiles, their images and focus.
        if (arranged.shown != shown) shown = arranged.shown
        renderCurrentPage(arranged)
        showWhyEmpty(arranged)
    }

    private fun isInitialFavorite(app: InstalledApp): Boolean {
        val label = app.label.trim().lowercase(Locale.ROOT)
        val packageName = app.launchable.packageName.lowercase(Locale.ROOT)
        return label.contains("youtube") || label.contains("spotify") || label.contains("twitch") ||
            packageName.startsWith("com.google.android.youtube") ||
            packageName.startsWith("com.spotify.") ||
            packageName.startsWith("tv.twitch.")
    }

    private fun initialFavoriteOrder(app: InstalledApp): Int {
        val label = app.label.lowercase(Locale.ROOT)
        return when {
            label.contains("youtube") -> 0
            label.contains("spotify") -> 1
            else -> 2
        }
    }

    private fun selectPage(next: Page) {
        if (arrange.active) arrange.end()
        val previousFocus = (tiles.focusedChild as? AppTileView)?.app
        val changed = page != next
        page = next
        updateDockSelection()
        refresh()
        if (changed) {
            val index = (0 until tiles.childCount).firstOrNull { (tiles.getChildAt(it) as AppTileView).app == previousFocus }
                ?: 0
            tiles.getChildAt(index)?.requestFocus()
        }
    }

    private fun renderCurrentPage(arranged: ArrangedApps) {
        val favorites = arranged.shown.mapTo(HashSet()) { it.launchable }
        when (page) {
            Page.HOME -> {
                findViewById<TextView>(R.id.home_title).text = getString(R.string.rabbit_tv_title)
                pageHint.visibility = View.GONE
                findViewById<View>(R.id.home_empty).visibility = View.GONE
                show(arranged.shown, favorites)
            }
            Page.APPS -> {
                findViewById<TextView>(R.id.home_title).text = getString(R.string.rabbit_tv_apps_title)
                pageHint.text = getString(R.string.rabbit_tv_apps_hint)
                pageHint.visibility = View.VISIBLE
                val allApps = (arranged.shown + arranged.hidden)
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                findViewById<View>(R.id.home_empty).visibility = if (allApps.isEmpty()) View.VISIBLE else View.GONE
                show(allApps, favorites)
            }
        }
    }

    private fun updateDockSelection() {
        setDockSelected(homeEntry, page == Page.HOME)
        setDockSelected(appsEntry, page == Page.APPS)
    }

    private fun setupDockEntry(entry: TextView) {
        entry.setOnFocusChangeListener { view, _ -> setDockSelected(view as TextView, view.isSelected) }
    }

    private fun setDockSelected(entry: TextView, selected: Boolean) {
        entry.isSelected = selected
        val density = resources.displayMetrics.density
        val focused = entry.hasFocus()
        entry.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = density * 12f
            setColor(if (selected) 0x664B3150 else if (focused) 0x334B3150 else Color.TRANSPARENT)
            if (selected || focused) setStroke((density * if (focused) 2 else 1).toInt(), 0xCCFF7449.toInt())
        }
        entry.compoundDrawablesRelative.filterNotNull().forEach { drawable ->
            drawable.mutate().setTint(if (selected || focused) 0xFFFF9A77.toInt() else Color.WHITE)
        }
    }

    private fun show(appearance: Appearance) {
        tiles.tileGeometry = appearance.tileGeometry
        tiles.tileStyle = appearance.tileStyle
        topBar.appearance = appearance.topBar
        wallpaper.show(appearance.background)
    }

    /** Arrange mode ended: the tiles show [arranged] already, unless apps changed meanwhile. */
    private fun arrangingEnded(arranged: ArrangedApps) {
        this.arranged = arranged
        shown = arranged.shown
        showWhyEmpty(arranged)
        if (appsChangedWhileArranging) {
            appsChangedWhileArranging = false
            refresh()
        }
    }

    private fun show(apps: List<InstalledApp>, favorites: Set<LaunchableApp>) {
        val focused = (tiles.focusedChild as? AppTileView)?.app?.launchable
        // If focus is on a dock or header item, leave it there; otherwise restore the focused tile.
        val topBarFocused = topBar.hasFocus()
        val dockFocused = homeEntry.hasFocus() || appsEntry.hasFocus() || settingsEntry.hasFocus()
        // Apps still there keep their tile and its image: installing one app draws one banner.
        val kept = (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }.associateBy { it.app }
        tiles.removeAllViews()
        for (app in apps) {
            val tile = kept[app] ?: AppTileView(this, app, images, tiles.paints)
            tile.setOnClickListener { clicked ->
                val selected = (clicked as AppTileView).app
                if (page == Page.HOME) open(selected) else toggleFavorite(selected)
            }
            tile.setOnLongClickListener(if (page == Page.HOME) startArranging else null)
            tile.favorite = app.launchable in favorites
            tile.hidden = app.launchable !in favorites
            tile.contentDescription = when {
                page == Page.APPS && app.launchable in favorites -> "${app.label}, favorita"
                page == Page.APPS -> "${app.label}, no favorita. Pulsa OK para añadirla a Inicio"
                else -> app.label
            }
            tile.invalidate()
            if (tile.id == View.NO_ID) tile.id = View.generateViewId()
            tiles.addView(tile)
        }
        tiles.getChildAt(0)?.id?.let { firstTileId ->
            homeEntry.nextFocusRightId = firstTileId
            appsEntry.nextFocusRightId = firstTileId
        }
        shown = apps
        val index = apps.indexOfFirst { it.launchable == focused }.coerceAtLeast(0)
        if (!topBarFocused && !dockFocused) tiles.getChildAt(index)?.requestFocus()
    }

    private fun toggleFavorite(app: InstalledApp) {
        val current = arranged ?: return
        val next = if (app in current.shown) current.hide(app) else current.show(app)
        arrangements.save(next.arrangement)
        arranged = next
        shown = next.shown
        renderCurrentPage(next)
        (0 until tiles.childCount).firstOrNull { (tiles.getChildAt(it) as AppTileView).app == app }
            ?.let { tiles.getChildAt(it)?.requestFocus() }
    }

    /** Without tiles, says why: nothing installed, or everything hidden (and where to show apps again). */
    private fun showWhyEmpty(arranged: ArrangedApps) {
        if (page == Page.APPS) {
            empty.text = getString(R.string.home_no_apps)
            empty.visibility = if (arranged.shown.isEmpty() && arranged.hidden.isEmpty()) View.VISIBLE else View.GONE
            return
        }
        if (arranged.shown.isNotEmpty()) {
            empty.visibility = View.GONE
            return
        }
        empty.text = if (arranged.hidden.isEmpty()) getString(R.string.home_no_apps) else getString(R.string.home_all_hidden)
        empty.visibility = View.VISIBLE
    }

    private fun open(app: InstalledApp) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setClassName(app.launchable.packageName, app.launchable.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            refresh()   // uninstalled since the tiles were read: drop its tile
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // The Menu key opens the settings too, on remotes that have one; the top bar's entry is the
    // way on those that don't. On release, and only for a press that started here: not the end
    // of a press that closed something else.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_MENU) return super.onKeyDown(keyCode, event)
        event.startTracking()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_MENU) return super.onKeyUp(keyCode, event)
        if (event.isTracking && !event.isCanceled) openSettings()
        return true
    }

    // Back on Android 15 (API 35) and older; onCreate handles Android 16, which lint doesn't see.
    // Deprecated, but its replacements need AndroidX (OnBackPressedCallback) or API 33
    // (OnBackInvokedCallback). Doesn't call super, which would close the home screen.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = arrange.back()
}
