package com.example.ui.screens

import android.util.Patterns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.AccountContactDetails
import com.example.auth.attemptAutoSignIn
import com.example.auth.consumePendingRegistration
import com.example.auth.getTestLoginPreset
import com.example.auth.loadAccountDetails
import com.example.auth.loadSavedPortalRole
import com.example.auth.onGoogleSignInClicked
import com.example.auth.saveAccountDetails
import com.example.auth.savePendingRegistration
import com.example.auth.savePortalRole
import com.example.auth.signOut
import com.example.model.AccountRole
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private enum class AuthEntryMode(val label: String) {
    CREATE_ACCOUNT("Create Account"),
    SIGN_IN("Sign In")
}

private enum class ContactMethod(val label: String) {
    MOBILE("Mobile Number"),
    MAIL_ID("Mail ID")
}

@Composable
fun AuthScreen(
    onPortalAuthenticated: (AccountRole) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val existingUser = Firebase.auth.currentUser
    val existingDetails = remember(existingUser?.uid) {
        if (existingUser != null) loadAccountDetails(context, existingUser.uid) else AccountContactDetails()
    }

    var selectedRole by remember { mutableStateOf(AccountRole.BUYER) }
    var entryMode by remember(existingUser?.uid) {
        mutableStateOf(
            if (existingUser != null && (existingDetails.phoneNumber.isNotBlank() || existingDetails.email.isNotBlank())) {
                AuthEntryMode.SIGN_IN
            } else {
                AuthEntryMode.CREATE_ACCOUNT
            }
        )
    }
    var contactMethod by remember { mutableStateOf(ContactMethod.MOBILE) }
    var fullNameInput by remember(existingUser?.uid) {
        mutableStateOf(
            existingDetails.displayName.ifBlank { existingUser?.displayName.orEmpty() }
        )
    }
    var mobileInput by remember(existingUser?.uid) {
        mutableStateOf(existingDetails.phoneNumber)
    }
    var mailIdInput by remember(existingUser?.uid) {
        mutableStateOf(
            existingDetails.email.ifBlank { existingUser?.email.orEmpty() }
        )
    }

    var isLoading by remember { mutableStateOf(false) }
    var isCheckingAutoSignIn by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingAutoSignIn = false
                val uid = Firebase.auth.currentUser?.uid
                val savedRole = if (!uid.isNullOrBlank()) loadSavedPortalRole(context, uid) else null
                if (savedRole != null) {
                    onPortalAuthenticated(savedRole)
                }
            },
            onUnauthenticated = {
                isCheckingAutoSignIn = false
            },
            scope = scope
        )
    }

    fun validateAndBuildRegistration(): AccountContactDetails? {
        val cleanName = fullNameInput.trim()
        if (cleanName.length < 2) {
            errorMessage = "Please enter your full name (at least 2 characters)."
            return null
        }

        return when (contactMethod) {
            ContactMethod.MOBILE -> {
                val rawPhone = mobileInput.trim()
                val digitsOnly = rawPhone.filter { it.isDigit() }
                if (digitsOnly.length !in 10..15 || !rawPhone.matches(Regex("^\\+?[0-9\\s\\-()]{10,20}$"))) {
                    errorMessage = "Please enter a valid mobile number (10 to 15 digits)."
                    return null
                }
                val formattedPhone = if (rawPhone.startsWith("+") || digitsOnly.length > 10) {
                    rawPhone.take(24)
                } else {
                    "+91 $digitsOnly"
                }
                AccountContactDetails(
                    displayName = cleanName,
                    phoneNumber = formattedPhone,
                    email = mailIdInput.trim()
                )
            }

            ContactMethod.MAIL_ID -> {
                val cleanMail = mailIdInput.trim()
                if (cleanMail.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(cleanMail).matches()) {
                    errorMessage = "Please enter a valid Mail ID (e.g., name@example.com)."
                    return null
                }
                AccountContactDetails(
                    displayName = cleanName,
                    phoneNumber = mobileInput.trim(),
                    email = cleanMail
                )
            }
        }
    }

    fun applyTestLoginPreset(role: AccountRole, autoSignIn: Boolean) {
        val preset = getTestLoginPreset(role)
        val details = preset.toContactDetails()
        errorMessage = null
        selectedRole = role
        fullNameInput = preset.displayName
        mobileInput = preset.phoneNumber
        mailIdInput = preset.email

        if (!autoSignIn) {
            entryMode = AuthEntryMode.CREATE_ACCOUNT
            return
        }

        if (existingUser != null) {
            saveAccountDetails(context, existingUser.uid, details)
            savePortalRole(context, existingUser.uid, role)
            onPortalAuthenticated(role)
        } else {
            savePendingRegistration(context, details)
            isLoading = true
            onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isLoading = false
                    val uid = Firebase.auth.currentUser?.uid
                    val consumed = consumePendingRegistration(context) ?: details
                    if (!uid.isNullOrBlank()) {
                        saveAccountDetails(context, uid, consumed)
                        savePortalRole(context, uid, role)
                    }
                    onPortalAuthenticated(role)
                },
                onAuthError = { msg ->
                    isLoading = false
                    errorMessage = msg
                },
                scope = scope,
                onAuthCancelled = {
                    isLoading = false
                }
            )
        }
    }

    val accentColor = if (selectedRole == AccountRole.BUYER) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                    )
                )
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_storefront),
                    contentDescription = "Lakshmi Puja Needs, Fancy and Grocery Storefront",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                        .height(148.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 6.dp,
                    border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.surface),
                    modifier = Modifier.size(72.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_lakshmi_logo),
                        contentDescription = "Lakshmi Brand Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalFlorist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Puja Needs, Fancy & Groceries",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (entryMode == AuthEntryMode.CREATE_ACCOUNT) {
                    "Create your Buyer or Seller account using a Mobile Number or Mail ID."
                } else {
                    "Choose your account portal to sign in separately as a Buyer or Seller."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Entry mode switch right on startup: Create Account | Sign In
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_mode_segmented_row")
            ) {
                AuthEntryMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = entryMode == mode,
                        onClick = {
                            errorMessage = null
                            entryMode = mode
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AuthEntryMode.entries.size
                        ),
                        icon = {
                            Icon(
                                imageVector = if (mode == AuthEntryMode.CREATE_ACCOUNT) {
                                    Icons.Filled.PersonAdd
                                } else {
                                    Icons.Filled.AccountCircle
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag(
                            if (mode == AuthEntryMode.CREATE_ACCOUNT) {
                                "auth_tab_create_account"
                            } else {
                                "auth_tab_sign_in"
                            }
                        )
                    ) {
                        Text(
                            text = mode.label,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Separate Buyer & Seller Account Portal Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PortalSelectorCard(
                    title = if (entryMode == AuthEntryMode.CREATE_ACCOUNT) "Buyer Account" else "Buyer Login",
                    subtitle = "Browse live stock, wishlist & order groceries",
                    icon = Icons.Filled.ShoppingBag,
                    selected = selectedRole == AccountRole.BUYER,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    onClick = { selectedRole = AccountRole.BUYER },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("select_buyer_portal_card")
                )

                PortalSelectorCard(
                    title = if (entryMode == AuthEntryMode.CREATE_ACCOUNT) "Seller Account" else "Seller Login",
                    subtitle = "Manage +/- stock, alerts & orders",
                    icon = Icons.Filled.Storefront,
                    selected = selectedRole == AccountRole.SELLER,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { selectedRole = AccountRole.SELLER },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("select_seller_portal_card")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (entryMode == AuthEntryMode.CREATE_ACCOUNT) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (selectedRole == AccountRole.BUYER) {
                                "Create Buyer Account"
                            } else {
                                "Create Seller Account"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )

                        OutlinedTextField(
                            value = fullNameInput,
                            onValueChange = {
                                fullNameInput = it
                                errorMessage = null
                            },
                            label = { Text("Full Name") },
                            placeholder = {
                                Text(
                                    if (selectedRole == AccountRole.BUYER) {
                                        "e.g., Priya Sharma"
                                    } else {
                                        "e.g., Lakshmi Store Owner"
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(imageVector = Icons.Filled.Person, contentDescription = null)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_account_name_input")
                        )

                        Text(
                            text = "Register with Mobile Number or Mail ID",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterChip(
                                selected = contactMethod == ContactMethod.MOBILE,
                                onClick = {
                                    contactMethod = ContactMethod.MOBILE
                                    errorMessage = null
                                },
                                label = { Text(ContactMethod.MOBILE.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.PhoneAndroid,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("contact_method_mobile_chip")
                            )

                            FilterChip(
                                selected = contactMethod == ContactMethod.MAIL_ID,
                                onClick = {
                                    contactMethod = ContactMethod.MAIL_ID
                                    errorMessage = null
                                },
                                label = { Text(ContactMethod.MAIL_ID.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Email,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("contact_method_email_chip")
                            )
                        }

                        if (contactMethod == ContactMethod.MOBILE) {
                            OutlinedTextField(
                                value = mobileInput,
                                onValueChange = {
                                    mobileInput = it
                                    errorMessage = null
                                },
                                label = { Text("Mobile Number") },
                                placeholder = { Text("+91 98765 43210") },
                                supportingText = {
                                    Text("Enter a 10-digit mobile number (optional +country code)")
                                },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Filled.Call, contentDescription = null)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_account_mobile_input")
                            )
                        } else {
                            OutlinedTextField(
                                value = mailIdInput,
                                onValueChange = {
                                    mailIdInput = it
                                    errorMessage = null
                                },
                                label = { Text("Mail ID") },
                                placeholder = { Text("name@example.com") },
                                supportingText = {
                                    Text("Enter your email address for order & account alerts")
                                },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Filled.AlternateEmail, contentDescription = null)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_account_email_input")
                            )
                        }
                    }
                }
            } else {
                // Highlights Card tailored to selected portal in Sign In view
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (selectedRole == AccountRole.BUYER) {
                                "Inside Buyer Application"
                            } else {
                                "Inside Seller Application"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = accentColor
                        )

                        if (selectedRole == AccountRole.BUYER) {
                            PortalFeatureLine("Live Present Stock, Wishlist & quick Cart checkout")
                            PortalFeatureLine("Recent Order status card, OTP handoff & Delivery tracking")
                            PortalFeatureLine("Rate stores (1–5 stars) and listen to catalogs aloud")
                        } else {
                            PortalFeatureLine("Quick +/- inventory steppers and low-stock push alerts")
                            PortalFeatureLine("OTP / Photo delivery confirmation & tamper-proof audit log")
                            PortalFeatureLine("JSON backup/restore, delivery map & Gemini AI Sahayak")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (existingUser != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val displayIdentity = existingDetails.displayName
                            .ifBlank { existingUser.displayName ?: existingUser.email ?: "Google User" }
                        val contactLine = listOfNotNull(
                            existingDetails.phoneNumber.takeIf { it.isNotBlank() },
                            existingDetails.email.ifBlank { existingUser.email.orEmpty() }.takeIf { it.isNotBlank() }
                        ).joinToString(" • ")

                        Text(
                            text = "Account linked: $displayIdentity",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (contactLine.isNotBlank()) {
                            Text(
                                text = contactLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                errorMessage = null
                                if (entryMode == AuthEntryMode.CREATE_ACCOUNT) {
                                    val details = validateAndBuildRegistration() ?: return@Button
                                    saveAccountDetails(context, existingUser.uid, details)
                                }
                                savePortalRole(context, existingUser.uid, selectedRole)
                                onPortalAuthenticated(selectedRole)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("continue_selected_portal_button")
                        ) {
                            Text(
                                text = if (entryMode == AuthEntryMode.CREATE_ACCOUNT) {
                                    "Save & Enter ${if (selectedRole == AccountRole.BUYER) "Buyer" else "Seller"} App"
                                } else {
                                    if (selectedRole == AccountRole.BUYER) {
                                        "Enter Buyer Application"
                                    } else {
                                        "Enter Seller Application"
                                    }
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (entryMode == AuthEntryMode.SIGN_IN) {
                                OutlinedButton(
                                    onClick = { entryMode = AuthEntryMode.CREATE_ACCOUNT },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit Mobile/Mail", maxLines = 1)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    signOut(context = context, credentialManager = credentialManager, scope = scope) {
                                        entryMode = AuthEntryMode.CREATE_ACCOUNT
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Switch Account", maxLines = 1)
                            }
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        errorMessage = null
                        val pendingDetails = if (entryMode == AuthEntryMode.CREATE_ACCOUNT) {
                            validateAndBuildRegistration() ?: return@Button
                        } else {
                            null
                        }
                        if (pendingDetails != null) {
                            savePendingRegistration(context, pendingDetails)
                        }
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                val uid = Firebase.auth.currentUser?.uid
                                val consumed = consumePendingRegistration(context) ?: pendingDetails
                                if (!uid.isNullOrBlank()) {
                                    if (consumed != null) {
                                        saveAccountDetails(context, uid, consumed)
                                    }
                                    savePortalRole(context, uid, selectedRole)
                                }
                                onPortalAuthenticated(selectedRole)
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag(
                            if (entryMode == AuthEntryMode.CREATE_ACCOUNT) {
                                "create_account_submit_button"
                            } else if (selectedRole == AccountRole.SELLER) {
                                "google_sign_in_seller_button"
                            } else {
                                "google_sign_in_buyer_button"
                            }
                        ),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            if (entryMode == AuthEntryMode.CREATE_ACCOUNT) {
                                "Creating account..."
                            } else {
                                "Signing in..."
                            }
                        )
                    } else if (entryMode == AuthEntryMode.CREATE_ACCOUNT) {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(id = R.string.create_account_cta),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (selectedRole == AccountRole.SELLER) {
                                stringResource(id = R.string.sign_in_seller)
                            } else {
                                stringResource(id = R.string.sign_in_buyer)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (entryMode == AuthEntryMode.CREATE_ACCOUNT) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Your Mobile Number / Mail ID is saved to your Lakshmi account profile and verified securely via Google Sign-In.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Test Login (QA) Card for Buyer & Seller verification
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_login_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.test_login_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "QA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = if (existingUser != null) {
                            "1-tap launch into Test Buyer or Test Seller using your linked Google session."
                        } else {
                            "Pre-fills QA test profile & signs in with your Google account (no separate app password needed)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Buyer Test ID: buyer.test@lakshmi.example (+91 9876500002)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Seller Test ID: seller.test@lakshmi.example (+91 9876500001)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { applyTestLoginPreset(AccountRole.BUYER, autoSignIn = true) },
                            enabled = !isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("quick_test_buyer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ShoppingBag,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.test_login_buyer),
                                maxLines = 1,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        FilledTonalButton(
                            onClick = { applyTestLoginPreset(AccountRole.SELLER, autoSignIn = true) },
                            enabled = !isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("quick_test_seller_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.test_login_seller),
                                maxLines = 1,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { applyTestLoginPreset(selectedRole, autoSignIn = false) },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("autofill_test_credentials_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${stringResource(id = R.string.test_login_autofill)} (${selectedRole.title})",
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Protected by Google Sign-In & OTP Guard",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PortalSelectorCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accentColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PortalFeatureLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
