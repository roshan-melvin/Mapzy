package com.swapmap.zwap.demo

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import com.swapmap.zwap.R

/**
 * WakeWordService — Background Porcupine wake-word listener.
 *
 * Runs silently as a foreground service (required by Android for background mic access).
 * Sends a LOCAL broadcast [ACTION_WAKE_WORD_DETECTED] when the keyword is heard so
 * MainActivity can trigger the voice-report pipeline.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  SETUP REQUIRED (one-time, free):                                       │
 * │  1. Go to https://console.picovoice.ai — sign up (free tier is enough) │
 * │  2. Copy your AccessKey                                                 │
 * │  3. Go to "Keyword" → create "hey zwap"                                 │
 * │  4. Download the .ppn file for Android                                  │
 * │  5. Put the .ppn file in app/src/main/assets/hey_zwap_android.ppn      │
 * │  6. Paste your AccessKey into PORCUPINE_ACCESS_KEY below                │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
class WakeWordService : Service() {

    companion object {
        const val ACTION_WAKE_WORD_DETECTED = "com.swapmap.zwap.WAKE_WORD_DETECTED"
        private const val TAG = "WakeWordService"
        private const val CHANNEL_ID = "mapzy_wake_word_v2"
        private const val NOTIF_ID = 7001

        // ─────────────────────────────────────────────────────────────────────
        // Picovoice AccessKey
        private const val PORCUPINE_ACCESS_KEY = "yfq6Ap2Jc94dH4r+7Q7sIhEj8eSDZI7CTFt8dHeYwr458s0PVXRZyg=="

        // Custom keyword file (placed in app/src/main/assets/)
        // Wake phrase: "Hey Mapzy"  (trained as "hey map zee")
        private const val KEYWORD_MODEL = "hey-map-zee_en_android_v4_0_0.ppn"

        // Detection sensitivity 0.0–1.0 (higher = more triggers, less misses)
        private const val SENSITIVITY = 0.65f

        // Set to true  → uses a built-in keyword (no .ppn file needed, just AccessKey)
        // Set to false → uses the custom KEYWORD_MODEL from assets/
        private const val USE_BUILTIN = false   // using custom "Hey Mapzy" keyword
        // ─────────────────────────────────────────────────────────────────────
    }

    private var porcupine: Porcupine? = null
    private var audioRecord: android.media.AudioRecord? = null
    private var isListening = false
    private var listenerThread: Thread? = null

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, 
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, 
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand — starting wake-word listener")
        startListening()
        return START_STICKY   // Re-start if killed
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy — stopping wake-word listener")
        stopListening()
        super.onDestroy()
    }

    // ─── Core Listening Logic ────────────────────────────────────────────────

    private fun startListening() {
        if (isListening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted — stopping service")
            stopSelf()
            return
        }

        try {
            // ── Porcupine init ─────────────────────────────────────────────────
            // Using a built-in keyword so no custom .ppn file is needed.
            // Available built-in keywords:
            //   BLUEBERRY, BUMBLEBEE, GRASSHOPPER, JARVIS, PICOVOICE,
            //   PORCUPINE, TERMINATOR, AMERICANO, ALEXA, GRAPEFRUIT
            //
            // To use a CUSTOM "Hey Zwap" keyword later:
            //   1. Go to console.picovoice.ai → Keyword → create phrase
            //   2. Download the .ppn for Android
            //   3. Put it in app/src/main/assets/hey_zwap_android.ppn
            //   4. Set USE_BUILTIN = false below and rebuild
            // ─────────────────────────────────────────────────────────────────

            porcupine = if (USE_BUILTIN) {
                Porcupine.Builder()
                    .setAccessKey(PORCUPINE_ACCESS_KEY)
                    .setKeyword(Porcupine.BuiltInKeyword.JARVIS)   // Say "Jarvis" to trigger
                    .setSensitivity(SENSITIVITY)
                    .build(this)
            } else {
                Porcupine.Builder()
                    .setAccessKey(PORCUPINE_ACCESS_KEY)
                    .setKeywordPath(KEYWORD_MODEL)                 // Custom .ppn from assets/
                    .setSensitivity(SENSITIVITY)
                    .build(this)
            }

            val frameLength = porcupine!!.frameLength
            val sampleRate = porcupine!!.sampleRate

            val bufferSize = android.media.AudioRecord.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            )

            audioRecord = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord!!.startRecording()
            isListening = true
            Log.i(TAG, "✅ Porcupine started — listening for 'Hey Mapzy'")

            listenerThread = Thread {
                val frame = ShortArray(frameLength)
                while (isListening) {
                    val read = audioRecord?.read(frame, 0, frameLength) ?: 0
                    if (read == frameLength) {
                        try {
                            val keywordIndex = porcupine?.process(frame) ?: -1
                            if (keywordIndex >= 0) {
                                Log.i(TAG, "🔔 Wake word detected!")
                                broadcastDetected()
                            }
                        } catch (e: PorcupineException) {
                            Log.e(TAG, "Porcupine processing error", e)
                        }
                    }
                }
            }.also { it.start() }

        } catch (e: PorcupineException) {
            Log.e(TAG, "Failed to init Porcupine: ${e.message}", e)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening: ${e.message}", e)
            stopSelf()
        }
    }

    private fun stopListening() {
        isListening = false
        listenerThread?.interrupt()
        listenerThread = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (_: Exception) {}
        try {
            porcupine?.delete()
            porcupine = null
        } catch (_: Exception) {}
    }

    private fun broadcastDetected() {
        val intent = Intent(ACTION_WAKE_WORD_DETECTED)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // ─── Foreground Notification (required for background mic access) ────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mapzy Wake Word",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Listening for 'Hey Mapzy' in the background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mapzy is listening")
            .setContentText(if (USE_BUILTIN) "Say 'Jarvis' to report a hazard" else "Say 'Hey Mapzy' to report a hazard")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()
    }
}
