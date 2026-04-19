package com.swapmap.zwap.demo

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RelativeLayout
import com.swapmap.zwap.R

/**
 * Enum representing the different page states in the navigation app.
 * Each state has a specific FAB configuration.
 */
enum class PageState {
    /** Home/Explore page - idle map state: Speedometer left, Hazard/Compass/Recenter right */
    HOME_EXPLORE,
    /** Directions page - route planning: Voice+Speedometer left, Recenter right only */
    DIRECTIONS,
    /** Location Info page - place selected: Same as Home (Speedometer left, Hazard/Compass/Recenter right) */
    LOCATION_INFO,
    /** Start/Navigation page - active navigation: Only Speedometer visible */
    START_NAVIGATION
}

/**
 * Manages FAB positioning and visibility across different page states.
 * 
 * FAB Layout by Page (based on reference images):
 * - Home/Explore: Speedometer (left), Hazard/Compass/Recenter (right) - NO Voice Assistant
 * - Location Info: Same as Home - Speedometer (left), Hazard/Compass/Recenter (right)
 * - Directions: Voice Assistant + Speedometer (left), Recenter only (right) - Hazard/Compass hidden
 * - Start/Navigation: Only Speedometer visible
 * 
 * Key features:
 * - Uses fixed absolute positioning anchored to safe area insets only
 * - FAB visibility changes happen AFTER page transition completes
 * - Prevents FAB flickering during transitions
 * - Ensures Voice Assistant renders ABOVE Speedometer (z-order)
 */
class FabLayoutManager(private val activity: Activity) {
    
    private val handler = Handler(Looper.getMainLooper())
    
    // FAB references (lazily initialized)
    private val fabAiVoice: View? by lazy { activity.findViewById(R.id.fab_ai_voice) }
    private val speedLimitWidget: View? by lazy { activity.findViewById(R.id.speed_limit_widget) }
    private val fabStackContainer: View? by lazy { activity.findViewById(R.id.fab_stack_container) }
    private val fabHazard: View? by lazy { activity.findViewById(R.id.btn_toggle_osm) }
    private val fabCompass: View? by lazy { activity.findViewById(R.id.fab_compass) }
    private val fabRecenter: View? by lazy { activity.findViewById(R.id.fab_recenter) }
    
    // Current page state
    private var currentState: PageState = PageState.HOME_EXPLORE
    
    // Flag to prevent visibility changes during transition
    private var isTransitioning = false
    
    // Transition delay in milliseconds (matches typical page transition animation)
    private val TRANSITION_DELAY_MS = 300L
    
    // Fixed positions in dp (converted to pixels at runtime)
    private val FAB_MARGIN_SIDE = 16 // dp
    private val FAB_MARGIN_BOTTOM_ABOVE_NAV = 96 // dp - above bottom nav bar
    private val SPEEDOMETER_BOTTOM_MARGIN = 96 // dp
    private val VOICE_ASSISTANT_BOTTOM_MARGIN = 260 // dp - above speedometer (96+152dp widget height+12dp gap)
    private val FAB_STACK_BOTTOM_MARGIN = 96 // dp
    
    /**
     * Gets the current page state.
     */
    fun getCurrentState(): PageState = currentState
    
    /**
     * Sets the page state and updates FAB layout accordingly.
     * Visibility changes are deferred until after the transition animation completes.
     * 
     * @param newState The new page state
     * @param immediate If true, applies changes immediately without transition delay
     */
    fun setPageState(newState: PageState, immediate: Boolean = false) {
        if (currentState == newState && !immediate) return
        
        currentState = newState
        
        if (immediate) {
            applyFabLayoutForState(newState)
        } else {
            // Mark as transitioning to prevent intermediate visibility changes
            isTransitioning = true
            
            // Hide FABs immediately to prevent glitching during transition
            hideFabsDuringTransition()
            
            // Apply new layout after transition completes using post-frame callback
            handler.postDelayed({
                isTransitioning = false
                applyFabLayoutForState(newState)
            }, TRANSITION_DELAY_MS)
        }
    }
    
    /**
     * Forces immediate FAB layout update without transition delay.
     * Re-anchors all FABs to their correct positions for the current state.
     */
    fun forceUpdateLayout() {
        // Cancel any pending delayed callback from setPageState() to prevent double animation
        cancelPendingChanges()
        isTransitioning = false
        resetFabPositions()
        applyFabLayoutForState(currentState)
    }
    
    /**
     * Resets FAB positions to their fixed coordinates.
     * Called when returning to Home to prevent position drift.
     */
    private fun resetFabPositions() {
        val density = activity.resources.displayMetrics.density
        
        // Reset speedometer position (left side, above bottom nav)
        speedLimitWidget?.let { view ->
            val params = view.layoutParams as? RelativeLayout.LayoutParams
            params?.let {
                it.removeRule(RelativeLayout.ABOVE)
                it.addRule(RelativeLayout.ALIGN_PARENT_START)
                it.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                it.marginStart = (FAB_MARGIN_SIDE * density).toInt()
                it.bottomMargin = (SPEEDOMETER_BOTTOM_MARGIN * density).toInt()
                view.layoutParams = it
            }
        }
        
        // Reset voice assistant position (left side, above speedometer)
        fabAiVoice?.let { view ->
            val params = view.layoutParams as? RelativeLayout.LayoutParams
            params?.let {
                it.removeRule(RelativeLayout.ABOVE)
                it.addRule(RelativeLayout.ALIGN_PARENT_START)
                it.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                it.marginStart = (FAB_MARGIN_SIDE * density).toInt()
                it.bottomMargin = (VOICE_ASSISTANT_BOTTOM_MARGIN * density).toInt()
                view.layoutParams = it
            }
        }
        
        // Reset fab stack container position (right side, above bottom nav)
        fabStackContainer?.let { view ->
            val params = view.layoutParams as? RelativeLayout.LayoutParams
            params?.let {
                it.removeRule(RelativeLayout.ABOVE)
                it.addRule(RelativeLayout.ALIGN_PARENT_END)
                it.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                it.marginEnd = (FAB_MARGIN_SIDE * density).toInt()
                it.bottomMargin = (FAB_STACK_BOTTOM_MARGIN * density).toInt()
                view.layoutParams = it
            }
        }
        
        // Ensure proper z-order: Voice Assistant above Speedometer
        fabAiVoice?.bringToFront()
        // Note: alpha is NOT reset here so setFabVisibility() can animate the fade-in
    }
    
    /**
     * Hides all FABs during page transition to prevent flickering.
     */
    private fun hideFabsDuringTransition() {
        // Set alpha to 0 instead of GONE to maintain layout positions
        fabAiVoice?.alpha = 0f
        speedLimitWidget?.alpha = 0f
        fabStackContainer?.alpha = 0f
    }
    
    /**
     * Applies the FAB layout configuration for the given page state.
     */
    private fun applyFabLayoutForState(state: PageState) {
        // Always reset positions first to prevent drift
        resetFabPositions()
        
        when (state) {
            PageState.HOME_EXPLORE -> applyHomeExploreFabLayout()
            PageState.DIRECTIONS -> applyDirectionsFabLayout()
            PageState.LOCATION_INFO -> applyLocationInfoFabLayout()
            PageState.START_NAVIGATION -> applyStartNavigationFabLayout()
        }
    }
    
    /**
     * Home/Explore page - 5 FABs visible (based on reference image 1):
     * Left side: Voice Assistant (upper), Speedometer (lower)
     * Right side: Hazard (upper), Compass (mid), Recenter (lower)
     */
    private fun applyHomeExploreFabLayout() {
        // Left column - Voice Assistant + Speedometer
        setFabVisibility(fabAiVoice, View.VISIBLE)
        setFabVisibility(speedLimitWidget, View.VISIBLE)
        
        // Right column FABs (all visible on Home/Explore)
        setFabVisibility(fabStackContainer, View.VISIBLE)
        fabHazard?.visibility = View.VISIBLE
        fabCompass?.visibility = View.VISIBLE
        fabRecenter?.visibility = View.VISIBLE
        
    }
    
    /**
     * Location Info page - Same as Directions (based on reference image 2):
     * Left side: Voice Assistant (upper), Speedometer (lower)
     * Right side: Recenter ONLY (Hazard and Compass hidden)
     */
    private fun applyLocationInfoFabLayout() {
        // Same layout as Directions (3 FABs)
        applyDirectionsFabLayout()
    }
    
    /**
     * Directions page - 3 FABs visible (based on reference image 2):
     * Left side: Voice Assistant (upper), Speedometer (lower)
     * Right side: Recenter ONLY (Hazard and Compass hidden)
     */
    private fun applyDirectionsFabLayout() {
        // Left column FABs - Voice Assistant + Speedometer
        setFabVisibility(fabAiVoice, View.VISIBLE)
        setFabVisibility(speedLimitWidget, View.VISIBLE)
        
        // Right column - only Recenter visible, Hazard and Compass hidden
        setFabVisibility(fabStackContainer, View.VISIBLE)
        fabHazard?.visibility = View.GONE
        fabCompass?.visibility = View.GONE
        fabRecenter?.visibility = View.VISIBLE
        
        // Ensure Voice Assistant is above Speedometer
        fabAiVoice?.bringToFront()
    }
    
    /**
     * Start/Navigation page - Only Speedometer visible (based on reference image 3):
     * No Voice Assistant, No Recenter, No Hazard, No Compass
     */
    private fun applyStartNavigationFabLayout() {
        // Start page: Speedometer + Mic + Recenter visible. Hazard and Compass hidden.
        // Ensure translationY is 0 — may still carry over from directions panel offset
        fabAiVoice?.translationY = 0f
        speedLimitWidget?.translationY = 0f
        fabStackContainer?.translationY = 0f
        setFabVisibility(fabAiVoice, View.VISIBLE)
        setFabVisibility(speedLimitWidget, View.VISIBLE)
        setFabVisibility(fabStackContainer, View.VISIBLE)
        fabHazard?.visibility = View.GONE
        fabCompass?.visibility = View.GONE
        fabRecenter?.visibility = View.VISIBLE
        fabAiVoice?.bringToFront()
    }
    
    /**
     * Sets FAB visibility with optional fade animation.
     */
    private fun setFabVisibility(view: View?, targetVisibility: Int) {
        if (view == null) return
        
        if (view.visibility == targetVisibility && view.alpha == 1f) return
        
        if (targetVisibility == View.VISIBLE) {
            view.visibility = View.VISIBLE
            view.alpha = 0f
            view.animate()
                .alpha(1f)
                .setDuration(150)
                .start()
        } else {
            view.animate()
                .alpha(0f)
                .setDuration(100)
                .withEndAction {
                    view.visibility = targetVisibility
                }
                .start()
        }
    }
    
    /**
     * Prepares for transition to Start page by deferring UI composition.
     * Ensures the Start page is fully laid out before becoming visible.
     * 
     * @param onReadyCallback Called when the Start page UI is ready to be displayed
     */
    fun prepareStartPageTransition(onReadyCallback: () -> Unit) {
        isTransitioning = true
        
        // Reset translationY immediately — coming from directions page, FABs may be
        // lifted above the panel (e.g. translationY=-617). Reset before hiding so they
        // snap back to XML-anchored position, not off-screen when made visible again.
        fabAiVoice?.translationY = 0f
        speedLimitWidget?.translationY = 0f
        fabStackContainer?.translationY = 0f

        // Hide current FABs immediately to prevent flash
        fabAiVoice?.visibility = View.INVISIBLE
        fabAiVoice?.alpha = 0f
        speedLimitWidget?.alpha = 0f
        fabStackContainer?.visibility = View.INVISIBLE
        fabStackContainer?.alpha = 0f
        
        // Use post-frame callback to ensure UI is fully composed
        activity.window.decorView.post {
            handler.postDelayed({
                currentState = PageState.START_NAVIGATION
                isTransitioning = false
                applyStartNavigationFabLayout()
                onReadyCallback()
            }, 50L) // Small delay to ensure composition is complete
        }
    }
    
    /**
     * Handles transition back FROM Start page to a target page.
     * Immediately applies the correct FAB layout without showing wrong FABs.
     * 
     * @param targetState The page state to transition to
     */
    fun exitStartPageToState(targetState: PageState) {
        // Immediately set state without transition delay
        currentState = targetState
        isTransitioning = false
        
        // Reset positions and apply layout immediately
        resetFabPositions()
        applyFabLayoutForState(targetState)
    }
    
    /**
     * Returns true if a page transition is currently in progress.
     */
    fun isTransitionInProgress(): Boolean = isTransitioning
    
    /**
     * Cancels any pending visibility changes.
     */
    fun cancelPendingChanges() {
        handler.removeCallbacksAndMessages(null)
        isTransitioning = false
    }
    
    /**
     * Cleans up resources. Call this in Activity.onDestroy().
     */
    fun cleanup() {
        cancelPendingChanges()
    }
}
