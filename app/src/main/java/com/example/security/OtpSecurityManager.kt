package com.example.security

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import java.security.MessageDigest
import java.security.SecureRandom

data class OtpChallenge(
    val actionLabel: String,
    val codeHash: String,
    val previewCode: String,
    val expiresAtMillis: Long,
    val attemptsLeft: Int = 3
)

object OtpSecurityManager {
    private const val PREFS_NAME = "shubhmart_security_prefs"
    private const val KEY_PIN_HASH = "seller_pin_hash"
    private const val CHANNEL_ID = "shubhmart_otp_channel"
    private const val OTP_VALIDITY_MS = 90_000L
    private val secureRandom = SecureRandom()

    fun createChallenge(actionLabel: String): OtpChallenge {
        val codeInt = secureRandom.nextInt(900_000) + 100_000
        val code = codeInt.toString()
        return OtpChallenge(
            actionLabel = actionLabel,
            codeHash = sha256(code),
            previewCode = code,
            expiresAtMillis = System.currentTimeMillis() + OTP_VALIDITY_MS,
            attemptsLeft = 3
        )
    }

    fun verifyCode(challenge: OtpChallenge, enteredCode: String): Boolean {
        if (System.currentTimeMillis() > challenge.expiresAtMillis) return false
        if (challenge.attemptsLeft <= 0) return false
        val clean = enteredCode.trim()
        if (clean.length != 6) return false
        return sha256(clean) == challenge.codeHash
    }

    fun hasSellerPin(context: Context, uid: String): Boolean {
        return !context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("${KEY_PIN_HASH}_$uid", null)
            .isNullOrBlank()
    }

    fun setSellerPin(context: Context, uid: String, pin: String) {
        val clean = pin.trim()
        require(clean.length in 4..6 && clean.all { it.isDigit() }) {
            "Seller Security PIN must be 4 to 6 digits."
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("${KEY_PIN_HASH}_$uid", sha256("$uid:$clean"))
            .apply()
    }

    fun clearSellerPin(context: Context, uid: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("${KEY_PIN_HASH}_$uid")
            .apply()
    }

    fun verifySellerPin(context: Context, uid: String, enteredPin: String): Boolean {
        val savedHash = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("${KEY_PIN_HASH}_$uid", null) ?: return true
        return sha256("$uid:${enteredPin.trim()}") == savedHash
    }

    fun postOtpNotification(context: Context, code: String, actionLabel: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lakshmi Security OTP",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "One-time passcodes for protected delete and inventory changes"
            }
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Lakshmi Security OTP: $code")
            .setContentText("Valid 90s to authorize: $actionLabel")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(7104, notification)
        return true
    }

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
