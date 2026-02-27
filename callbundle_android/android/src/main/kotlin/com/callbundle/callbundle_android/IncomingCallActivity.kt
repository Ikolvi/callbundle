package com.callbundle.callbundle_android

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat

/**
 * Native Android Activity displayed over the lock screen for incoming calls.
 *
 * Shows ONLY the incoming call UI (caller info, accept/decline buttons) over
 * the lock screen. The main Flutter Activity is never exposed over the lock
 * screen, preventing full app access without device unlock.
 *
 * ## Features
 *
 * - **Lock screen display**: `showWhenLocked` + `turnScreenOn` on THIS Activity only
 * - **Keyguard dismissal**: On accept, prompts user to unlock (PIN/biometric/swipe)
 *   before launching the main app. If no secure lock, proceeds immediately.
 * - **Swipe to answer**: Swipe up on the accept button area to answer
 * - **Pulsating avatar**: Animated concentric rings behind the avatar circle
 * - **Connecting state**: Shows "Connecting..." while waiting for keyguard dismissal
 * - **Auto-dismiss**: Dismissed externally when call is cancelled/ended/timed-out
 *
 * ## Lifecycle
 *
 * 1. Launched by full-screen intent from incoming call notification
 * 2. Shows caller info with Accept/Decline buttons + swipe gesture
 * 3. On Accept:
 *    a. If device locked → shows unlock prompt → on success proceeds
 *    b. If device unlocked → proceeds immediately
 *    c. Finishes this Activity, delegates to [CallBundlePlugin.onCallAccepted]
 * 4. On Decline: finishes, delegates to [CallBundlePlugin.onCallDeclined]
 * 5. Auto-dismissed via [dismissIfShowing] when call state changes
 */
class IncomingCallActivity : Activity() {

    companion object {
        private const val TAG = "IncomingCallActivity"

        @Volatile
        var current: IncomingCallActivity? = null

        /**
         * Dismisses the incoming call Activity if currently showing.
         * Safe to call from any thread.
         */
        fun dismissIfShowing() {
            val activity = current ?: return
            try {
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        activity.cleanupAnimations()
                        activity.finishAndRemoveTask()
                        Log.d(TAG, "dismissIfShowing: Dismissed")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "dismissIfShowing: Failed", e)
            }
        }
    }

    // ── Call data ──
    private var callId: String? = null
    private var callerName: String = "Unknown"
    private var callType: Int = 0
    private var callerAvatar: String? = null
    private var callExtra: Bundle? = null

    // ── UI state ──
    private var isConnecting = false
    private var swipeStartY = 0f

    // ── UI references for state transitions ──
    private var acceptGroupView: LinearLayout? = null
    private var declineGroupView: LinearLayout? = null
    private var buttonSectionView: LinearLayout? = null
    private var swipeHintView: TextView? = null

    // ── Animations ──
    private val pulseAnimators = mutableListOf<AnimatorSet>()
    private val handler = Handler(Looper.getMainLooper())

    // ── Density helper ──
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this

        setupWindow()
        extractCallData(intent)

        if (callId == null) {
            Log.e(TAG, "onCreate: No callId in intent, finishing")
            finish()
            return
        }

        buildUI()
        Log.d(TAG, "onCreate: Showing for callId=$callId, caller=$callerName, type=$callType")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            cleanupAnimations()
            isConnecting = false
            extractCallData(it)
            buildUI()
            Log.d(TAG, "onNewIntent: Updated to callId=$callId, caller=$callerName")
        }
    }

    override fun onDestroy() {
        if (current == this) current = null
        cleanupAnimations()
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!isConnecting) handleDecline()
    }

    // region Window Setup

    private fun setupWindow() {
        // Show over lock screen WITHOUT dismissing keyguard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Blend system bars with gradient background
        window.statusBarColor = Color.parseColor("#1B1B2F")
        window.navigationBarColor = Color.parseColor("#1F4068")
    }

    // endregion

    // region Data Extraction

    private fun extractCallData(intent: Intent) {
        callId = intent.getStringExtra("callId")
        callerName = intent.getStringExtra("callerName") ?: "Unknown"
        callType = intent.getIntExtra("callType", 0)
        callerAvatar = intent.getStringExtra("callerAvatar")
        callExtra = intent.getBundleExtra("callExtra")
    }

    // endregion

    // region User Actions

    private fun handleAccept() {
        if (isConnecting) return
        val id = callId ?: return
        Log.d(TAG, "handleAccept: callId=$id")

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

        if (km != null && km.isKeyguardLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Device is locked — ask user to unlock first
            isConnecting = true
            showConnectingState()

            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    Log.d(TAG, "handleAccept: Keyguard dismissed, proceeding")
                    runOnUiThread { proceedWithAccept(id) }
                }

                override fun onDismissCancelled() {
                    Log.d(TAG, "handleAccept: Keyguard dismiss cancelled")
                    runOnUiThread {
                        isConnecting = false
                        buildUI() // Restore incoming call UI
                    }
                }

                override fun onDismissError() {
                    Log.w(TAG, "handleAccept: Keyguard dismiss error, proceeding anyway")
                    runOnUiThread { proceedWithAccept(id) }
                }
            })
        } else {
            // Device unlocked or pre-API 26 — proceed directly
            proceedWithAccept(id)
        }
    }

    private fun proceedWithAccept(id: String) {
        cleanupAnimations()

        // CRITICAL: Launch the main Activity with "call_accepted" action
        // FROM this IncomingCallActivity (which is in the foreground).
        //
        // This is essential because:
        // 1. IncomingCallActivity is the foreground Activity, so it has
        //    the OS-level BAL (Background Activity Launch) exemption.
        // 2. If we call finish() first and then try to start the main
        //    Activity from the plugin (application context), Android 10+
        //    blocks the launch as a "background" activity start.
        // 3. By launching from HERE, we reuse the same proven code path
        //    as the notification Accept button (onNewIntent handler).
        //
        // The onNewIntent handler in CallBundlePlugin will:
        //   - Cancel the notification
        //   - Stop the ringtone
        //   - Dismiss this IncomingCallActivity (safety net)
        //   - Send the "accepted" event to Dart
        //   - Navigate to the in-app call screen
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            launchIntent?.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                putExtra("callId", id)
                putExtra("action", "call_accepted")
                callExtra?.let { putExtra("callExtra", it) }
            }
            if (launchIntent != null) {
                startActivity(launchIntent)
                Log.d(TAG, "proceedWithAccept: Launched main Activity for callId=$id")
            } else {
                Log.w(TAG, "proceedWithAccept: Launch intent null, using fallback")
                // Fallback: call plugin directly (may not bring app to foreground)
                CallBundlePlugin.instance?.onCallAccepted(id, callExtra?.let { bundle ->
                    mutableMapOf<String, Any>().also { map ->
                        bundle.keySet().forEach { key ->
                            map[key] = bundle.getString(key) ?: ""
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "proceedWithAccept: Failed to launch main app", e)
            // Last resort: try plugin directly
            CallBundlePlugin.instance?.onCallAccepted(id, null)
        }

        finish()
    }

    private fun handleDecline() {
        val id = callId ?: return
        Log.d(TAG, "handleDecline: callId=$id")

        cleanupAnimations()

        val plugin = CallBundlePlugin.instance
        if (plugin != null) {
            plugin.onCallDeclined(id)
        } else {
            // Plugin null — cancel notification directly
            Log.d(TAG, "handleDecline: Plugin null, cancelling notification")
            NotificationManagerCompat.from(this).cancel(id.hashCode())
            // Also stop ringtone via static helper if possible
            NotificationHelper.stopStaticRingtone()
        }

        finish()
    }

    // endregion

    // region UI Construction

    private fun buildUI() {
        // ─── Root Layout ───
        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#1B1B2F"),
                    Color.parseColor("#162447"),
                    Color.parseColor("#1F4068")
                )
            )
        }

        // ─── Caller Section (centered, offset above middle) ───
        val callerSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Avatar container with pulsating rings
        val avatarSize = dp(96)
        val avatarContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(200), dp(200)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(24)
            }
        }

        // Add 3 pulsating ring views behind the avatar
        buildPulseRings(avatarContainer, avatarSize)

        // Avatar circle with initials
        val initials = callerName
            .split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
            .joinToString("")
            .ifEmpty { "?" }

        val avatarView = TextView(this).apply {
            text = initials
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#3F51B5"))
            }
            layoutParams = FrameLayout.LayoutParams(avatarSize, avatarSize, Gravity.CENTER)
        }
        avatarContainer.addView(avatarView)
        callerSection.addView(avatarContainer)

        // Caller name
        val nameView = TextView(this).apply {
            text = callerName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
                marginStart = dp(32)
                marginEnd = dp(32)
            }
        }
        callerSection.addView(nameView)

        // Call type label
        val typeLabel = if (callType == 1) "Incoming Video Call" else "Incoming Voice Call"
        val typeView = TextView(this).apply {
            text = typeLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
        }
        callerSection.addView(typeView)

        val callerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply { bottomMargin = dp(100) }
        root.addView(callerSection, callerParams)

        // ─── Bottom Section (buttons + swipe hint) ───
        val bottomContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Button row
        val buttonSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        buttonSectionView = buttonSection

        val buttonSize = dp(64)

        // Decline button group
        val declineGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        declineGroupView = declineGroup

        val declineBtn = createCircleButton(
            size = buttonSize,
            bgColor = Color.parseColor("#E53935"),
            iconText = "\u2715", // ✕
            description = "Decline call"
        )
        declineBtn.setOnClickListener { handleDecline() }
        declineGroup.addView(declineBtn)

        val declineLabel = TextView(this).apply {
            text = "Decline"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        declineGroup.addView(declineLabel)

        buttonSection.addView(
            declineGroup,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(80) }
        )

        // Accept button group (with swipe gesture)
        val acceptGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        acceptGroupView = acceptGroup

        val acceptBtn = createCircleButton(
            size = buttonSize,
            bgColor = Color.parseColor("#43A047"),
            iconText = "\u2713", // ✓
            description = "Accept call"
        )
        // Accept button handles both tap and swipe-up
        setupSwipeGesture(acceptBtn)
        acceptGroup.addView(acceptBtn)

        val acceptLabel = TextView(this).apply {
            text = "Accept"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        acceptGroup.addView(acceptLabel)

        buttonSection.addView(acceptGroup)
        bottomContainer.addView(buttonSection)

        // Swipe hint
        val swipeHint = TextView(this).apply {
            text = "\u25B2  Swipe up to answer" // ▲ Swipe up to answer
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#7E8A9E"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(24) }
        }
        swipeHintView = swipeHint
        bottomContainer.addView(swipeHint)

        // Animate the swipe hint (gentle bob)
        startSwipeHintAnimation(swipeHint)

        val bottomParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = dp(60) }
        root.addView(bottomContainer, bottomParams)

        setContentView(root)
    }

    // endregion

    // region Pulse Ring Animation

    /**
     * Creates 3 concentric ring views behind the avatar that pulse outward
     * with staggered delays, creating a "calling" ripple effect.
     */
    private fun buildPulseRings(container: FrameLayout, avatarSize: Int) {
        for (i in 0..2) {
            val ring = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(2), Color.parseColor("#4F5B93"))
                }
                alpha = 0f
                layoutParams = FrameLayout.LayoutParams(avatarSize, avatarSize, Gravity.CENTER)
            }
            container.addView(ring)

            val scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 2.5f).apply {
                repeatCount = ValueAnimator.INFINITE
            }
            val scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 2.5f).apply {
                repeatCount = ValueAnimator.INFINITE
            }
            val alpha = ObjectAnimator.ofFloat(ring, "alpha", 0.6f, 0f).apply {
                repeatCount = ValueAnimator.INFINITE
            }

            val animSet = AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                duration = 2500
                startDelay = i * 800L
                start()
            }
            pulseAnimators.add(animSet)
        }
    }

    // endregion

    // region Swipe Gesture

    /**
     * Attaches a touch listener that handles both taps and swipe-up gestures.
     *
     * - **Tap**: Calls [handleAccept] immediately
     * - **Swipe up** (>100dp): Calls [handleAccept] with visual feedback
     * - **Partial swipe** (<100dp): Snaps back with spring animation
     */
    @Suppress("ClickableViewAccessibility")
    private fun setupSwipeGesture(button: View) {
        val swipeThreshold = dp(100).toFloat()

        button.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartY = event.rawY
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = swipeStartY - event.rawY // positive = up
                    if (dy > 0) {
                        // Visual feedback: move the accept group up
                        val translation = (dy * 0.5f).coerceAtMost(swipeThreshold)
                        acceptGroupView?.translationY = -translation
                        // Fade swipe hint
                        swipeHintView?.alpha = 1f - (dy / swipeThreshold).coerceIn(0f, 1f)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    val dy = swipeStartY - event.rawY

                    if (dy > swipeThreshold) {
                        // Swipe threshold reached — accept
                        handleAccept()
                    } else if (Math.abs(dy) < dp(10)) {
                        // Small movement — treat as tap
                        handleAccept()
                    } else {
                        // Partial swipe — snap back
                        acceptGroupView?.animate()
                            ?.translationY(0f)
                            ?.setDuration(200)
                            ?.start()
                        swipeHintView?.animate()
                            ?.alpha(1f)
                            ?.setDuration(200)
                            ?.start()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    acceptGroupView?.animate()
                        ?.translationY(0f)
                        ?.setDuration(200)
                        ?.start()
                    swipeHintView?.animate()
                        ?.alpha(1f)
                        ?.setDuration(200)
                        ?.start()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Starts a gentle up-down bob animation on the swipe hint text.
     */
    private fun startSwipeHintAnimation(view: TextView) {
        ObjectAnimator.ofFloat(view, "translationY", 0f, -dp(6).toFloat()).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    // endregion

    // region Connecting State

    /**
     * Transitions the UI to a "Connecting..." state while waiting for
     * keyguard dismissal. Replaces the action buttons with a status label.
     */
    private fun showConnectingState() {
        // Fade out buttons
        buttonSectionView?.animate()
            ?.alpha(0f)
            ?.setDuration(300)
            ?.withEndAction {
                buttonSectionView?.visibility = View.GONE
            }
            ?.start()

        swipeHintView?.animate()
            ?.alpha(0f)
            ?.setDuration(200)
            ?.start()

        // Show connecting text where buttons were
        val connectingText = TextView(this).apply {
            text = "Connecting..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }

        // Animate dots
        var dotCount = 0
        val dotRunnable = object : Runnable {
            override fun run() {
                if (isConnecting && !isFinishing) {
                    dotCount = (dotCount + 1) % 4
                    connectingText.text = "Connecting" + ".".repeat(dotCount)
                    handler.postDelayed(this, 500)
                }
            }
        }
        handler.postDelayed(dotRunnable, 500)

        // Add to parent of button section
        val parent = buttonSectionView?.parent as? LinearLayout
        parent?.addView(connectingText, 0)
    }

    // endregion

    // region Utilities

    /**
     * Creates a circular action button with a centered text icon.
     */
    private fun createCircleButton(
        size: Int,
        bgColor: Int,
        iconText: String,
        description: String
    ): TextView {
        return TextView(this).apply {
            text = iconText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = description
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            layoutParams = LinearLayout.LayoutParams(size, size)
            isClickable = true
            isFocusable = true
            elevation = 4f * density
        }
    }

    /**
     * Stops all running animations and clears handler callbacks.
     */
    private fun cleanupAnimations() {
        for (anim in pulseAnimators) {
            anim.cancel()
        }
        pulseAnimators.clear()
        handler.removeCallbacksAndMessages(null)
    }

    // endregion
}
