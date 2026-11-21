package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.model.AccountRole
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "AuthHelper"
private const val PREFS_NAME = "shubhmart_portal_prefs"
private const val KEY_ACTIVE_ROLE = "active_portal_role"
private const val KEY_DISPLAY_NAME = "account_display_name"
private const val KEY_PHONE_NUMBER = "account_phone_number"
private const val KEY_CONTACT_EMAIL = "account_contact_email"
private const val KEY_PENDING_NAME = "pending_reg_name"
private const val KEY_PENDING_PHONE = "pending_reg_phone"
private const val KEY_PENDING_EMAIL = "pending_reg_email"

data class AccountContactDetails(
    val displayName: String = "",
    val phoneNumber: String = "",
    val email: String = ""
)

data class TestAccountPreset(
    val role: AccountRole,
    val displayName: String,
    val phoneNumber: String,
    val email: String
) {
    fun toContactDetails(): AccountContactDetails = AccountContactDetails(
        displayName = displayName,
        phoneNumber = phoneNumber,
        email = email
    )
}

fun getTestLoginPreset(role: AccountRole): TestAccountPreset = when (role) {
    AccountRole.SELLER -> TestAccountPreset(
        role = AccountRole.SELLER,
        displayName = "Test Seller (Lakshmi)",
        phoneNumber = "+91 9876500001",
        email = "seller.test@lakshmi.example"
    )

    AccountRole.BUYER -> TestAccountPreset(
        role = AccountRole.BUYER,
        displayName = "Test Buyer (Priya)",
        phoneNumber = "+91 9876500002",
        email = "buyer.test@lakshmi.example"
    )
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun savePortalRole(context: Context, uid: String, role: AccountRole) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString("${KEY_ACTIVE_ROLE}_$uid", role.name)
        .apply()
}

fun loadSavedPortalRole(context: Context, uid: String): AccountRole? {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString("${KEY_ACTIVE_ROLE}_$uid", null)
        ?: return null
    return AccountRole.entries.firstOrNull { it.name == raw }
}

fun clearSavedPortalRole(context: Context, uid: String?) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (uid.isNullOrBlank()) {
        prefs.edit().clear().apply()
    } else {
        prefs.edit().remove("${KEY_ACTIVE_ROLE}_$uid").apply()
    }
}

fun savePendingRegistration(context: Context, details: AccountContactDetails) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_PENDING_NAME, details.displayName.trim())
        .putString(KEY_PENDING_PHONE, details.phoneNumber.trim())
        .putString(KEY_PENDING_EMAIL, details.email.trim())
        .apply()
}

fun consumePendingRegistration(context: Context): AccountContactDetails? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val name = prefs.getString(KEY_PENDING_NAME, "")?.trim().orEmpty()
    val phone = prefs.getString(KEY_PENDING_PHONE, "")?.trim().orEmpty()
    val email = prefs.getString(KEY_PENDING_EMAIL, "")?.trim().orEmpty()
    if (name.isEmpty() && phone.isEmpty() && email.isEmpty()) return null
    prefs.edit()
        .remove(KEY_PENDING_NAME)
        .remove(KEY_PENDING_PHONE)
        .remove(KEY_PENDING_EMAIL)
        .apply()
    return AccountContactDetails(displayName = name, phoneNumber = phone, email = email)
}

fun saveAccountDetails(context: Context, uid: String, details: AccountContactDetails) {
    if (uid.isBlank()) return
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    if (details.displayName.isNotBlank()) {
        editor.putString("${KEY_DISPLAY_NAME}_$uid", details.displayName.trim())
    }
    if (details.phoneNumber.isNotBlank()) {
        editor.putString("${KEY_PHONE_NUMBER}_$uid", details.phoneNumber.trim())
    }
    if (details.email.isNotBlank()) {
        editor.putString("${KEY_CONTACT_EMAIL}_$uid", details.email.trim())
    }
    editor.apply()
}

fun loadAccountDetails(context: Context, uid: String): AccountContactDetails {
    if (uid.isBlank()) return AccountContactDetails()
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return AccountContactDetails(
        displayName = prefs.getString("${KEY_DISPLAY_NAME}_$uid", "")?.trim().orEmpty(),
        phoneNumber = prefs.getString("${KEY_PHONE_NUMBER}_$uid", "")?.trim().orEmpty(),
        email = prefs.getString("${KEY_CONTACT_EMAIL}_$uid", "")?.trim().orEmpty()
    )
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val activity = context as? Activity ?: run {
                onUnauthenticated()
                return@launch
            }
            val result = withTimeoutOrNull(2500L) {
                credentialManager.getCredential(activity, request)
            }
            val cred = result?.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val activity = context as? Activity ?: run {
                onAuthError("Sign-in requires an active screen context.")
                return@launch
            }
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type received.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google account available on device: ${e.message}", e)
            onAuthError(
                "No Google account is signed in on this device/emulator. " +
                    "Open Android Settings -> Passwords & accounts -> Add Google account, then try again."
            )
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager Google Sign-In error", e)
            onAuthError(
                "Google Sign-In could not complete (${e.localizedMessage ?: "configuration or network error"}). " +
                    "Verify a Google account is signed in and Google Play Services is active."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed")
        }
    }
}

fun signOut(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    val uid = Firebase.auth.currentUser?.uid
    clearSavedPortalRole(context, uid)
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
