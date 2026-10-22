package com.luncher.launcher.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
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
    private lateinit var homeEntry: ImageButton
    private lateinit var appsEntry: ImageButton
    private lateinit var settingsEntry: ImageButton
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
        // Acknowledge only after the home view has resumed and refreshed successfully.
        HomeOverrideRuntime.lastHomeResumedAt = SystemClock.elapsedRealtime()
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

    private fun setupDockEntry(entry: ImageButton) {
        entry.setOnFocusChangeListener { view, _ -> setDockSelected(view as ImageButton, view.isSelected) }
    }

    private fun setDockSelected(entry: ImageButton, selected: Boolean) {
        entry.isSelected = selected
        val density = resources.displayMetrics.density
        val focused = entry.hasFocus()
        val indicator = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = density * 16f
            setColor(when {
                focused -> 0x40FF7449
                selected -> 0x20FF7449
                else -> Color.TRANSPARENT
            })
            if (focused) setStroke((density * 2).toInt(), 0xFFFF7449.toInt())
        }
        // Keep the indicator inside the 56dp entry so it frames the 28dp icon evenly.
        entry.background = InsetDrawable(indicator, (density * 4).toInt())
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
            tile.setOnClickListener { clicked -> open((clicked as AppTileView).app) }
            tile.setOnLongClickListener { clicked ->
                val selected = (clicked as AppTileView).app
                if (page == Page.HOME) startArranging.onLongClick(clicked)
                else {
                    toggleFavorite(selected)
                    true
                }
            }
            tile.favorite = app.launchable in favorites
            // Only the arranging shelf dims hidden apps; normal app browsing keeps every banner clear.
            tile.hidden = false
            tile.favo}|ï«h‘éì¶»§q«^v                .remove(HomeOverridePreferences.KEY_LAST_FAILURE_AT)
                .apply()
        } else {
            recordFailedAttempt()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != HomeOverridePreferences.GOOGLE_TV_LAUNCHER_PACKAGE) return
        if (!preferences.getBoolean(HomeOverridePreferences.KEY_ENABLED, false)) return

        // Never redirect from a system dialog, launcher menu or launcher settings surface.
        val className = event.className?.toString().orEmpty().lowercase(Locale.ROOT)
        if (className.contains("dialog") || className.contains("popup") ||
            className.contains("settings") || className.contains("menu")
        ) return

        val now = SystemClock.elapsedRealtime()
        if (pendingRequestAt != 0L || now - lastRequestAt < REQUEST_COOLDOWN_MS) return
        lastRequestAt = now

        while (recentRequests.peekFirst()?.let { now - it > REQUEST_WINDOW_MS } == true) {
            recentRequests.removeFirst()
        }
        if (recentRequests.size >= MAX_REQUESTS_IN_WINDOW) {
            disableForSafety("LauncherX and Rabbit TV appear to be cycling")
            return
        }
        recentRequests.addLast(now)
        pendingRequestAt = now

        try {
            startActivity(
                Intent(this, HomeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            )
            // Android may silently reject a background Activity start; check actual onResume.
            mainHandler.postDelayed(verifyRequest, VERIFY_DELAY_MS)
        } catch (exception: RuntimeException) {
            pendingRequestAt = 0L
            Log.w(TAG, "Rabbit TV launch request failed", exception)
            recordFailedAttempt()
        }
    }

    private fun recordFailedAttempt() {
        if (!preferences.getBoolean(HomeOverridePreferences.KEY_ENABLED, false)) return
        val now = System.currentTimeMillis()
        val previousAt = preferences.getLong(HomeOverridePreferences.KEY_LAST_FAILURE_AT, 0L)
        val previousCount = if (now >= previousAt && now - previousAt <= FAILURE_WINDOW_MS) {
            preferences.getInt(HomeOverridePreferences.KEY_FAILED_ATTEMPTS, 0)
        } else {
            0
        }
        val count = previousCount + 1
        preferences.edit()
            .putInt(HomeOverridePreferences.KEY_FAILED_ATTEMPTS, count)
            .putLong(HomeOverridePreferences.KEY_LAST_FAILURE_AT, now)
            .apply()
        if (count >= MAX_FAILED_ATTEMPTS) {
            disableForSafety("Activity did not reach foreground after $count requests")
        }
    }

    private fun disableForSafety(reason: String) {
        preferences.edit()
            .putBoolean(HomeOverridePreferences.KEY_ENABLED, false)
            .putBoolean(HomeOverridePreferences.KEY_SAFETY_STOP, true)
            .apply()
        recentRequests.clear()
        mainHandler.removeCallbacks(verifyRequest)
        pendingRequestAt = 0L
        Log.w(TAG, "Home override disabled for safety: $reason")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        mainHandler.removeCallbacks(verifyRequest)
        pendingRequestAt = 0L
        super.onDestroy()
    }

    private companion object {
        const val TAG = "RabbitHomeOverride"
        const val REQUEST_COOLDOWN_MS = 400L
        const val VERIFY_DELAY_MS = 1_000L
        const val MAX_FAILED_ATTEMPTS = 3
        const val FAILURE_WINDOW_MS = 90_000L
        const val REQUEST_WINDOW_MS = 30_000L
        const val MAX_REQUESTS_IN_WINDOW = 6
    }
}

/** In-process acknowledgement; automatically resets across process/device restarts. */
internal object HomeOverrideRuntime {
    @Volatile var lastHomeResumedAt: Long = 0L
}
