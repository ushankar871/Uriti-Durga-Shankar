package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.security.OtpChallenge
import com.example.security.OtpSecurityManager
import kotlinx.coroutines.delay

@Composable
fun OtpChallengeDialog(
    uid: String,
    actionLabel: String,
    isDestructive: Boolean,
    onDismiss: () -> Unit,
    onVerified: (keepSessionForTwoMinutes: Boolean) -> Unit
) {
    val context = LocalContext.current
    val hasPin = remember(uid) { OtpSecurityManager.hasSellerPin(context, uid) }

    var challenge by remember(actionLabel) { mutableStateOf<OtpChallenge?>(null) }
    var enteredOtp by remember { mutableStateOf("") }
    var enteredPin by remember { mutableStateOf("") }
    var showCodeOnDevice by remember { mutableStateOf(false) }
    var notificationSent by remember { mutableStateOf(false) }
    var keepSession by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var remainingSeconds by remember { mutableLongStateOf(0L) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val current = challenge
        if (granted && current != null) {
            notificationSent = OtpSecurityManager.postOtpNotification(context, current.previewCode, actionLabel)
        }
    }

    LaunchedEffect(challenge) {
        while (challenge != null) {
            val diffSec = ((challenge!!.expiresAtMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            remainingSeconds = diffSec
            if (diffSec <= 0L) {
                errorMsg = "OTP expired. Generate a fresh 6-digit OTP."
                break
            }
            delay(500L)
        }
    }

    fun issueOtp() {
        val fresh = OtpSecurityManager.createChallenge(actionLabel)
        challenge = fresh
        enteredOtp = ""
        errorMsg = null
        showCodeOnDevice = false

        val canNotify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (canNotify) {
            notificationSent = OtpSecurityManager.postOtpNotification(context, fresh.previewCode, actionLabel)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isDestructive) Icons.Filled.Lock else Icons.Filled.Security,
                contentDescription = null,
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (isDestructive) "OTP Required to Delete" else "OTP Required for Change",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDestructive) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                OutlinedButton(
                    onClick = { issueOtp() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_otp_button")
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (challenge == null) "Send 6-Digit OTP" else "Resend Fresh OTP (${remainingSeconds}s)")
                }

                if (challenge != null) {
                    if (notificationSent) {
                        Text(
                            text = "OTP notification sent to your device status bar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    if (hasPin && !showCodeOnDevice) {
                        OutlinedTextField(
                            value = enteredPin,
                            onValueChange = { enteredPin = it.filter { ch -> ch.isDigit() }.take(6) },
                            label = { Text("Enter Seller PIN to reveal OTP") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    TextButton(
                        onClick = {
                            if (hasPin && !OtpSecurityManager.verifySellerPin(context, uid, enteredPin)) {
                                errorMsg = "Incorrect Seller Security PIN."
                            } else {
                                errorMsg = null
                                showCodeOnDevice = !showCodeOnDevice
                            }
                        },
                        modifier = Modifier.testTag("reveal_otp_button")
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (showCodeOnDevice) {
                                "Active OTP: ${challenge!!.previewCode} (${remainingSeconds}s)"
                            } else {
                                "Reveal OTP on this device"
                            }
                        )
                    }

                    OutlinedTextField(
                        value = enteredOtp,
                        onValueChange = { enteredOtp = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Enter 6-Digit OTP") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("otp_code_input")
                    )

                    if (!isDestructive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = keepSession,
                                onCheckedChange = { keepSession = it }
                            )
                            Text(
                                text = "Keep OTP verified for 2 min (batch stock changes)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val active = challenge
                    if (active == null) {
                        errorMsg = "Tap 'Send 6-Digit OTP' first."
                        return@Button
                    }
                    if (OtpSecurityManager.verifyCode(active, enteredOtp)) {
                        onVerified(!isDestructive && keepSession)
                    } else {
                        val remaining = (active.attemptsLeft - 1).coerceAtLeast(0)
                        challenge = active.copy(attemptsLeft = remaining)
                        errorMsg = if (remaining == 0) {
                            "Too many invalid attempts. Request a new OTP."
                        } else {
                            "Invalid or expired OTP. Attempts left: $remaining"
                        }
                    }
                },
                enabled = challenge != null && enteredOtp.length == 6 && (challenge?.attemptsLeft ?: 0) > 0,
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.testTag("verify_otp_confirm_button")
            ) {
                Text(if (isDestructive) "Verify OTP & Delete" else "Verify OTP & Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
