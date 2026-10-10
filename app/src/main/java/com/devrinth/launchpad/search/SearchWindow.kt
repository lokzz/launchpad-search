package com.devrinth.launchpad.search

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.service.voice.VoiceInteractionSessionService
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.devrinth.launchpad.R
import com.devrinth.launchpad.activities.SettingsActivity
import com.devrinth.launchpad.adapters.PinnedActionAdapter
import com.devrinth.launchpad.adapters.PinnedActionListAdapter
import com.devrinth.launchpad.adapters.ResultScrollAdapter
import com.devrinth.launchpad.receivers.AssistantActionReceiver
import com.devrinth.launchpad.search.plugins.AppLaunchHistory
import com.devrinth.launchpad.search.plugins.AppUsageStats
import com.devrinth.launchpad.search.plugins.AssistantLaunch

class SearchWindow(val context: Context) {

    private lateinit var closeBtn : ImageButton
    private lateinit var settingsBtn : ImageButton
    private lateinit var debugBtn : ImageButton
    private lateinit var assistantBtn : ImageButton
    private lateinit var debugPanel : TextView

    /** Debug button stage: 0 off (hollow dot) -> 1 small (yellow) -> 2 full (green). */
    private var debugStage: Int = 0

    private lateinit var searchInput : EditText
    private lateinit var resultsView : RecyclerView

    private lateinit var pinnedResultView : RecyclerView
    private lateinit var pinnedAdapter : PinnedActionListAdapter
    private var pinnedArray = ArrayList<PinnedActionAdapter>()

    private lateinit var searchSuggestionsView: RecyclerView
    private lateinit var launchpadMainLayout: LinearLayout

    private lateinit var mSearchManager: SearchManager

    private var reloadReceiver : AssistantActionReceiver = AssistantActionReceiver {
        mSearchManager.reloadPlugins()
    }


    private var anim = AnimationUtils.loadAnimation(context, R.anim.pop_up)
    private var animOut = AnimationUtils.loadAnimation(context, R.anim.pop_down)

    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private var lastUiMode: Int = context.resources.configuration.uiMode
    private var isAlternateLayout: Boolean

    private lateinit var mContentView : View
    private lateinit var searchCardLayout: LinearLayout

    private var windowCloseAction: (() -> Unit)? = null

    init {
        val interpolator = PathInterpolatorCompat.create(0.425f, 0.130f, 0.130f, 0.975f)
        anim.interpolator = interpolator
        animOut.interpolator = interpolator

        isAlternateLayout = sharedPreferences.getBoolean("setting_layout_window_alternate", false)
    }

    private fun initViews(contentView : View) {

        launchpadMainLayout = contentView.findViewById(R.id.launchpad_main_layout)
        searchInput = contentView.findViewById(R.id.search_input)

        searchCardLayout = contentView.findViewById(R.id.search_card_layout)

        searchSuggestionsView = contentView.findViewById(R.id.search_suggestions_view)

        resultsView = contentView.findViewById(R.id.results_view)
//            pinnedResultView = contentView.findViewById(R.id.pinned_apps_view)
//            pinnedResultView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        closeBtn = contentView.findViewById(R.id.action_close)
        settingsBtn = contentView.findViewById(R.id.action_settings)
        debugBtn = contentView.findViewById(R.id.action_debug)
        assistantBtn = contentView.findViewById(R.id.action_assistant)
        debugPanel = contentView.findViewById(R.id.debug_panel)
        debugPanel.movementMethod = ScrollingMovementMethod.getInstance()

        if (sharedPreferences.getBoolean("setting_debug_button", false)) {
            debugBtn.visibility = View.VISIBLE
            debugStage = sharedPreferences.getInt("setting_debug_stage", 0).coerceIn(0, 2)
            applyDebugStage()
        }

        // Only shown when the toggle is on AND another assistant exists.
        if (sharedPreferences.getBoolean("setting_assistant_button", false) &&
            AssistantLaunch.findAssistantPackage(context) != null
        ) {
            assistantBtn.visibility = View.VISIBLE
        }

    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun initListeners() {
        closeBtn.setOnClickListener {
            hideWindow()
        }

        settingsBtn.setOnClickListener {
            hideWindow()
            context.startActivity(
                Intent(context, SettingsActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("from_launchpad", true))
        }

        debugBtn.setOnClickListener {
            // 3-stage toggle: off -> small -> full -> off.
            try {
                debugStage = (debugStage + 1) % 3
                sharedPreferences.edit { putInt("setting_debug_stage", debugStage) }
                applyDebugStage()
            } catch (e: Exception) {
                Log.e("DebugButton", "debug tap failed", e)
            }
        }

        assistantBtn.setOnClickListener {
            // Close first like the settings button: we're handing off to
            // another app, so the overlay must not linger on top of it.
            hideWindow()
            try {
                AssistantLaunch.openAssistant(context)
            } catch (e: Exception) {
                Log.e("AssistantButton", "assistant launch failed", e)
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(reloadReceiver, IntentFilter(AssistantActionReceiver.ACTION_OVERLAY_SHOW),
                VoiceInteractionSessionService.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(reloadReceiver, IntentFilter(AssistantActionReceiver.ACTION_OVERLAY_SHOW))
        }

        if (sharedPreferences.getBoolean("setting_close_on_outclick", false)) {
            launchpadMainLayout.setOnClickListener {
                this.hideWindow()
            }
        }

//            pinnedAdapter = PinnedActionListAdapter(pinnedArray, context)
//            pinnedResultView.adapter = pinnedAdapter
//
//            pinnedArray.add(
//                PinnedActionAdapter(
//                    "android.intent.action.VIEW",
//                    "https://google.com",
//                    AppCompatResources.getDrawable(context, R.drawable.web_search_24)
//                `)
//            )
//            pinnedArray.add(
//                PinnedActionAdapter(
//                    "android.intent.action.VIEW",
//                    "https://google.com",
//                    AppCompatResources.getDrawable(context, R.drawable.baseline_settings_24)
//                )
//            )`
//            pinnedAdapter.notifyDataSetChanged()
    }

    // PUBLIC
    fun onWindowClose(closeFn: () -> Unit) {
        windowCloseAction = closeFn
    }

    fun createLaunchpadWindow(window: Window?) {
        val inflater = context.getSystemService(VoiceInteractionSessionService.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        val contentView: View = if (isAlternateLayout) {
            inflater.inflate(R.layout.search_layout_reverse, null) }
        else {
            inflater.inflate(R.layout.search_layout, null)
        }

        mContentView = contentView
        initViews(contentView)

        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE) // kb
            if (sharedPreferences.getBoolean("setting_layout_blur_behind", false)) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.attributes?.blurBehindRadius = 25
                }
            }
            window.attributes = window.attributes
        }
        mSearchManager = SearchManager(
            context,
            searchInput,
            resultsView,
            searchSuggestionsView,
            searchCardLayout,
            isAlternateLayout
        ) { refreshDebugPanel() }

        initListeners()

    }

    fun fullscreen() {
        val searchCardView = mContentView.findViewById<CardView>(R.id.search_card_view)

        val params = searchCardView.layoutParams as LinearLayout.LayoutParams
        params.width = LinearLayout.LayoutParams.MATCH_PARENT
        params.height = LinearLayout.LayoutParams.MATCH_PARENT
        searchCardView.layoutParams = params

        searchCardView.requestLayout()
    }


    fun getLaunchpadWindow() : View {
        return mContentView
    }

    fun showKeyboard() {
        if (sharedPreferences.getBoolean("setting_search_show_keyboard", true)) {
            //searchInput.requestFocus()
            searchInput.isFocusableInTouchMode = true // kb
            searchInput.postDelayed({
                searchInput.requestFocus()

                val lManager =
                    context.getSystemService(VoiceInteractionSessionService.INPUT_METHOD_SERVICE) as InputMethodManager
                lManager.showSoftInput(searchInput, 0)
            }, 160)
        }
    }

    /** Re-renders the panel if a stage is active. Called on tap, on window
     * show (the window outlives opens, so open-time text would go stale),
     * and whenever the result list changes. */
    private fun refreshDebugPanel() {
        if (debugStage == 0 || !::debugPanel.isInitialized) return
        try {
            debugPanel.text = collectDebugInfo()
        } catch (e: Exception) {
            Log.e("DebugButton", "debug refresh failed", e)
        }
    }
    /** Applies the current debug stage: icon tint + panel visibility/content. */
    private fun applyDebugStage() {
        // No dot: the icon tint itself is the state. Default grey = off,
        // yellow = small panel, green = full panel.
        when (debugStage) {
            1 -> debugBtn.setColorFilter(
                ContextCompat.getColor(context, R.color.debug_small), PorterDuff.Mode.SRC_IN)
            2 -> debugBtn.setColorFilter(
                ContextCompat.getColor(context, R.color.debug_full), PorterDuff.Mode.SRC_IN)
            else -> debugBtn.clearColorFilter()
        }
        if (debugStage == 0) {
            debugPanel.visibility = View.GONE
        } else {
            debugPanel.visibility = View.VISIBLE
            debugPanel.scrollTo(0, 0)
            refreshDebugPanel()
        }
    }

    /** Small diagnostics snapshot for the debug panel. Kept lean on purpose. */
    private fun collectDebugInfo(): String {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        val query = if (::searchInput.isInitialized) searchInput.text.toString() else "?"
        val resultsAdapter = if (::resultsView.isInitialized) {
            resultsView.adapter as? ResultScrollAdapter
        } else {
            null
        }
        val resultCount = resultsAdapter?.itemCount ?: 0
        val breakdown = resultsAdapter?.resultPluginBreakdown()?.let { " ($it)" } ?: ""
        val plugins = sharedPreferences.getStringSet("setting_search_plugins", emptySet())
            .orEmpty().sorted().joinToString(",")
        val historyApps = try {
            AppLaunchHistory(context.applicationContext).snapshot().size
        } catch (_: Exception) {
            -1
        }
        val base = "v$version q='$query' results=$resultCount$breakdown\n" +
            "plugins=[$plugins]\nhistoryApps=$historyApps (id: [24h, 48h])"
        if (debugStage < 2) return base
        return base + collectHistoryDetail()
    }

    /** Per-app launch counts for the full stage (capped, panel scrolls). */
    private fun collectHistoryDetail(): String {
        val history = try {
            AppLaunchHistory(context.applicationContext).snapshot()
        } catch (_: Exception) {
            return ""
        }
        if (history.isEmpty()) return "\nno history yet"
        val now = System.currentTimeMillis()
        val lines = history.mapNotNull { (pkg, hits) ->
            val day = hits.count { it >= now - AppUsageStats.DAY_MILLIS }
            val twoDays = hits.count { it >= now - AppUsageStats.TOP_HIT_WINDOW_MILLIS }
            if (twoDays == 0) null
            else Triple(pkg.removePrefix("com."), day, twoDays)
        }.sortedWith(compareByDescending<Triple<String, Int, Int>> { it.third }
            .thenByDescending { it.second })
            .take(6)
        if (lines.isEmpty()) return "\nno history yet"
        return "\n" + lines.joinToString("\n") { "  ${it.first}: [${it.second}, ${it.third}]" }
    }

    fun unload() {
        mSearchManager.unloadPlugins()
    }    fun reload() {
        mSearchManager.reloadPlugins()
    }

    // -- WINDOW FUNCTION
    fun showWindow() {
        if (!mContentView.isShown)
            mContentView.startAnimation(anim)
        // The window object outlives opens; refresh stale panel text.
        // Result arrivals refresh it again via the SearchManager callback.
        refreshDebugPanel()
    }
    fun hideWindow() {
        mContentView.startAnimation(animOut)
        mContentView.postOnAnimationDelayed({
            mSearchManager.unloadPlugins()
            windowCloseAction?.invoke()
        }, 120)
    }

}