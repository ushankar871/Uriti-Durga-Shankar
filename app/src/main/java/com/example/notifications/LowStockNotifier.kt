package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.Product

object LowStockNotifier {

    private const val TAG = "LowStockNotifier"
    private const val CHANNEL_ID = "lakshmi_low_stock_alerts_v2"
    private const val CHANNEL_NAME = "Low Inventory Alerts"
    private const val CHANNEL_DESC =
        "Plays a subtle chime and alerts the Lakshmi store owner whenever grocery or puja items drop below the threshold."
    private const val NOTIFICATION_ID = 4001
    private const val PREFS_NAME = "lakshmi_alert_prefs"
    private const val KEY_SOUND_ENABLED = "low_stock_sound_enabled"
    private const val CHIME_COOLDOWN_MS = 1200L

    @Volatile
    private var lastChimeAtMillis: Long = 0L

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isAlertSoundEnabled(context: Context, sellerId: String): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("${KEY_SOUND_ENABLED}_$sellerId", true)
    }

    fun setAlertSoundEnabled(context: Context, sellerId: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("${KEY_SOUND_ENABLED}_$sellerId", enabled)
            .apply()
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                    setSound(defaultSoundUri, audioAttrs)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Plays a gentle acoustic chime when a new low-stock alert is generated while the seller
     * is actively monitoring inventory in the app.
     */
    fun playSubtleAlertChime(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastChimeAtMillis < CHIME_COOLDOWN_MS) return false
        lastChimeAtMillis = now

        return try {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 32)
            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    generator.release()
                } catch (_: Throwable) {
                }
            }, 350L)
            true
        } catch (t: Throwable) {
            Log.d(TAG, "Unable to play subtle low-stock chime: ${t.message}")
            false
        }
    }

    fun showLowStockAlert(
        context: Context,
        storeName: String,
        lowStockItems: List<Product>,
        threshold: Long,
        playChime: Boolean = false
    ): Boolean {
        if (playChime) {
            playSubtleAlertChime()
        }
        if (lowStockItems.isEmpty() || !hasNotificationPermission(context)) {
            return false
        }

        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val outCount = lowStockItems.count { !it.inStock || it.stockQty <= 0L }
        val lowOnlyCount = lowStockItems.size - outCount
        val title = when {
            outCount > 0 && lowOnlyCount > 0 ->
                "Lakshmi Alert: $lowOnlyCount low stock & $outCount out of stock"
            outCount > 0 ->
                "Lakshmi Alert: $outCount item${if (outCount == 1) "" else "s"} out of stock"
            else ->
                "Lakshmi Low Stock: ${lowStockItems.size} item${if (lowStockItems.size == 1) "" else "s"} ≤ $threshold units"
        }

        val summaryLines = lowStockItems.take(5).map { item ->
            val statusText = if (!item.inStock || item.stockQty <= 0L) {
                "OUT OF STOCK"
            } else {
                "${item.stockQty} ${item.unit} left"
            }
            "• ${item.name} (${item.categoryEnum().displayName}): $statusText"
        }
        val extraCount = lowStockItems.size - summaryLines.size
        val expandedText = buildString {
            append("$storeName inventory below threshold (≤ $threshold units):\n")
            append(summaryLines.joinToString("\n"))
            if (extraCount > 0) {
                append("\n+ $extraCount more item${if (extraCount == 1) "" else "s"}")
            }
        }
        val shortSummary = lowStockItems.take(3).joinToString(", ") {
            if (!it.inStock || it.stockQty <= 0L) "${it.name} (0)" else "${it.name} (${it.stockQty})"
        }

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(shortSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSound(defaultSoundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    fun cancelLowStockAlert(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.cancel(NOTIFICATION_ID)
    }
}
