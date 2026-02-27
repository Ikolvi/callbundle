package com.callbundle.callbundle_android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person

/**
 * OEM-adaptive notification builder for incoming and ongoing calls.
 *
 * This class replaces the 437-line `CallNotificationPlugin.kt` that was
 * previously maintained in the app's source code. By shipping this
 * inside the plugin, apps get reliable call notifications without
 * any app-level native code.
 *
 * ## Strategy
 *
 * 1. **API 31+ (Android 12+):** Uses `NotificationCompat.CallStyle.forIncomingCall()`
 *    which provides the native call-style notification.
 * 2. **API 26-30:** Standard high-priority notification with Accept/Decline buttons.
 * 3. **Budget OEMs:** Avoids `RemoteViews` entirely (known inflation failures).
 *    Uses the simplest notification layout for maximum compatibility.
 *
 * ## Notification ID Strategy
 *
 * Uses `callId.hashCode()` as the notification ID. This ensures:
 * - Updating: posting again with the same callId replaces (not duplicates).
 * - Canceling: can cancel by callId without tracking notification IDs.
 */
class NotificationHelper(
    private val context: Context,
    private val appName: String
) {
    companion object {
        private const val TAG = "NotificationHelper"
        private const val CHANNEL_ID = "callbundle_incoming_channel"
        private const val CHANNEL_NAME = "Incoming Calls"
        private const val ONGOING_CHANNEL_ID = "callbundle_ongoing_channel"
        private const val ONGOING_CHANNEL_NAME = "Ongoing Calls"

        // Static: shared across all NotificationHelper instances (main engine
        // + background FCM engine). The background engine's startSound()
        // creates the MediaPlayer; the main engine's stopRingtone() must
        // be able to stop it. Instance fields would fail because each engine
        // has its own NotificationHelper with its own null reference.
        @Volatile
        private var mediaPlayer: MediaPlayer? = null
        @Volatile
        private var vibrator: Vibrator? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Ensures the notification channels exist.
     *
     * Creates channels proactively during [CallBundlePlugin.configure],
     * not lazily during notification posting. This prevents the race
     * condition where a channel is deleted and not recreated in time.
     */
    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Incoming call channel (high importance for heads-up display)
        val incomingChannel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for incoming calls"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setBypassDnd(true)
            enableVibration(false) // We manage vibration manually
            setSound(null, null) // We manage sound manually
        }
        nm.createNotificationChannel(incomingChannel)

        // Ongoing call channel (default importance)
        val ongoingChannel = NotificationChannel(
            ONGOING_CHANNEL_ID,
            ONGOING_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for active/ongoing calls"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(ongoingChannel)

        Log.d(TAG, "ensureNotificationChannel: Channels created/verified")
    }

    /**
     * Shows an incoming call notification with OEM-adaptive strategy.
     */
    fun showIncomingCallNotification(
        callId: String,
        callerName: String,
        callType: Int,
        handle: String?,
        callerAvatar: String?,
        duration: Long,
        isOemAdaptive: Boolean,
        extra: Map<*, *> = emptyMap<String, Any>()
    ) {
        val notificationId = callId.hashCode()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(callerName)
            .setContentText(handle ?: if (callType == 1) "Video Call" else "Voice Call")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(createFullScreenIntent(callId), true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isOemAdaptive) {
            // Use CallStyle for Android 12+ on non-budget OEMs
            try {
                val callerPerson = Person.Builder()
                    .setName(callerName)
                    .setImportant(true)
                    .build()

                val declineIntent = createActionPendingIntent(callId, "decline", extra)
                // CRITICAL: Use PendingIntent.getActivity() for Accept,
                // same as addStandardActions. getBroadcast + startActivity
                // fails on Android 12+ BAL restrictions.
                val acceptIntent = createAcceptActivityPendingIntent(callId, extra)

                builder.setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        callerPerson,
                        declineIntent,
                        acceptIntent
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "CallStyle failed, falling back to standard", e)
                addStandardActions(builder, callId, extra)
            }
        } else {
            // Standard notification with action buttons (budget OEMs + older APIs)
            addStandardActions(builder, callId, extra)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            Log.d(TAG, "showIncomingCallNotification: Posted for callId=$callId id=$notificationId")
        } catch (e: SecurityException) {
            Log.e(TAG, "showIncomingCallNotification: Permission denied", e)
        }

        // Start ringtone and vibration
        startRingtone()

        // Auto-dismiss after timeout (safety net for when call_cancelled
        // FCM is delayed by Doze mode or not delivered). Uses the
        // duration parameter from the Dart side, clamped to 30-120s.
        val timeoutMs = duration.coerceIn(30_000, 120_000)
        mainHandler.postDelayed({
            cancelNotification(callId)
            stopRingtone()
            Log.d(TAG, "showIncomingCallNotification: Auto-dismissed after ${timeoutMs}ms for callId=$callId")
            // Notify Dart that the call timed out (missed)
            CallBundlePlugin.instance?.sendCallEvent(
                type = "timedOut",
                callId = callId,
                isUserInitiated = false
            )
        }, timeoutMs)
    }

    /**
     * Shows an ongoing call notification.
     */
    fun showOngoingCallNotification(
        callId: String,
        callerName: String,
        callType: Int
    ) {
        val notificationId = callId.hashCode()

        val builder = NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle(callerName)
            .setContentText(if (callType == 1) "Video Call" else "Voice Call")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setUsesChronometer(true)

        // Add end call action
        val endIntent = createActionPendingIntent(callId, "end")
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "End Call",
            endIntent
        )

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "showOngoingCallNotification: Permission denied", e)
        }
    }

    /**
     * Cancels a notification by call ID.
     * Also cancels any pending auto-timeout for this notification.
     */
    fun cancelNotification(callId: String) {
        val notificationId = callId.hashCode()
        NotificationManagerCompat.from(context).cancel(notificationId)
        // Cancel any pending auto-dismiss timeout
        mainHandler.removeCallbacksAndMessages(null)
        Log.d(TAG, "cancelNotification: callId=$callId id=$notificationId")
    }

    /**
     * Starts the default ringtone and vibration.
     *
     * Checks the ringer mode and adjusts behavior accordingly:
     * - NORMAL: sound + vibration
     * - VIBRATE: vibration only
     * - SILENT: nothing
     */
    fun startRingtone() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                startSound()
                startVibration()
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                startVibration()
            }
            AudioManager.RINGER_MODE_SILENT -> {
                // Do nothing
            }
        }
    }

    /**
     * Stops any playing ringtone and vibration.
     */
    fun stopRingtone() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "stopRingtone: Error stopping media player", e)
        }

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.e(TAG, "stopRingtone: Error stopping vibrator", e)
        }
    }

    /**
     * Releases all resources.
     */
    fun cleanup() {
        stopRingtone()
    }

    // region Private helpers

    private fun addStandardActions(
        builder: NotificationCompat.Builder,
        callId: String,
        extra: Map<*, *> = emptyMap<String, Any>()
    ) {
        val declineIntent = createActionPendingIntent(callId, "decline", extra)

        // CRITICAL: Accept uses PendingIntent.getActivity() instead of
        // getBroadcast(). This ensures the Activity launches directly when
        // the user taps Accept. Using getBroadcast() + startActivity() from
        // a BroadcastReceiver fails on Android 12+ and many OEMs (Samsung,
        // Xiaomi, OPPO) due to background activity launch restrictions.
        val acceptIntent = createAcceptActivityPendingIntent(callId, extra)

        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Decline",
            declineIntent
        )
        builder.addAction(
            android.R.drawable.sym_call_outgoing,
            "Accept",
            acceptIntent
        )
    }

    private fun createFullScreenIntent(callId: String): PendingIntent {
        // Launch the app's main activity for full-screen incoming call display.
        // Includes flags to show over lock screen and turn the screen on.
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        intent.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            // Flags to show over lock screen (pre-API 27 support)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                @Suppress("DEPRECATION")
                addFlags(
                    android.app.KeyguardManager::class.java.let {
                        0x00200000 // FLAG_SHOW_WHEN_LOCKED (deprecated but needed for API < 27)
                    } or
                    0x02000000 // FLAG_TURN_SCREEN_ON (deprecated but needed for API < 27)
                )
            }
            putExtra("callId", callId)
            putExtra("action", "full_screen")
        }

        return PendingIntent.getActivity(
            context,
            callId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createActionPendingIntent(
        callId: String,
        action: String,
        extra: Map<*, *> = emptyMap<String, Any>()
    ): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java).apply {
            this.action = "com.callbundle.ACTION_${action.uppercase()}"
            putExtra("callId", callId)
            // Embed call metadata so CallActionReceiver can persist it
            // for cold-start event delivery via PendingCallStore.
            if (extra.isNotEmpty()) {
                val bundle = Bundle()
                for ((key, value) in extra) {
                    bundle.putString(key.toString(), value?.toString() ?: "")
                }
                putExtra("callExtra", bundle)
            }
        }

        return PendingIntent.getBroadcast(
            context,
            "$callId$action".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Creates a PendingIntent that directly launches the app's main Activity
     * when the user taps Accept on the notification.
     *
     * Using [PendingIntent.getActivity] instead of [PendingIntent.getBroadcast]
     * is critical because:
     * - On Android 12+ (API 31), BroadcastReceivers cannot reliably start
     *   activities from the background (BAL restrictions).
     * - Many OEMs (Samsung, Xiaomi, OPPO) further restrict background activity
     *   starts from receivers.
     * - PendingIntent.getActivity from a notification action has a strong
     *   OS-level exemption that works on all devices.
     *
     * The launched Activity receives the intent via `onNewIntent()` (if already
     * running) or `onCreate()` (if killed). The plugin handles both via
     * [NewIntentListener] and [onAttachedToActivity].
     */
    private fun createAcceptActivityPendingIntent(
        callId: String,
        extra: Map<*, *> = emptyMap<String, Any>()
    ): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

        intent.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            putExtra("callId", callId)
            putExtra("action", "call_accepted")
            if (extra.isNotEmpty()) {
                val bundle = Bundle()
                for ((key, value) in extra) {
                    bundle.putString(key.toString(), value?.toString() ?: "")
                }
                putExtra("callExtra", bundle)
            }
        }

        return PendingIntent.getActivity(
            context,
            "${callId}accept".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startSound() {
        try {
            val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE
            ) ?: return

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "startSound: Failed to start ringtone", e)
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 1000, 500, 1000, 500)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startVibration: Failed to start vibration", e)
        }
    }

    // endregion
}
