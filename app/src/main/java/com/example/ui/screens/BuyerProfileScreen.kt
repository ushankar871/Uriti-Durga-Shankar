package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.AccountContactDetails
import com.example.auth.loadAccountDetails
import com.example.auth.saveAccountDetails
import com.example.model.BuyerAddress
import com.example.model.PaymentMethodType
import com.example.model.PreferredPaymentMethod
import com.example.model.UiState
import com.example.ui.BuyerViewModel

@Composable
fun BuyerProfileScreen(
    viewModel: BuyerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profileState by viewModel.accountProfileState.collectAsStateWithLifecycle()
    val addressesState by viewModel.addressesState.collectAsStateWithLifecycle()
    val paymentMethodsState by viewModel.paymentMethodsState.collectAsStateWithLifecycle()

    val savedLocalContact = remember(viewModel.buyerId) {
        loadAccountDetails(context, viewModel.buyerId)
    }
    val cloudProfile = (profileState as? UiState.Success)?.data
    val displayName = cloudProfile?.displayName?.ifBlank { null }
        ?: savedLocalContact.displayName.ifBlank { viewModel.buyerDisplayName }
    val email = cloudProfile?.email?.ifBlank { null } ?: savedLocalContact.email
    val phoneNumber = cloudProfile?.phoneNumber?.ifBlank { null } ?: savedLocalContact.phoneNumber

    val addresses = (addressesState as? UiState.Success)?.data ?: emptyList()
    val paymentMethods = (paymentMethodsState as? UiState.Success)?.data ?: emptyList()

    var showEditContactDialog by remember { mutableStateOf(false) }
    var editingAddress by remember { mutableStateOf<BuyerAddress?>(null) }
    var showAddAddressDialog by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<PreferredPaymentMethod?>(null) }
    var showAddPaymentDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "buyer_contact_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("buyer_profile_contact_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Person,
                                            contentDescription = "Buyer Avatar",
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Lakshmi Buyer Profile",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { showEditContactDialog = true },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("edit_contact_details_button")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit")
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Phone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = phoneNumber.ifBlank { "Add mobile number for courier updates" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (phoneNumber.isBlank()) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Email,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = email.ifBlank { "Add email ID for order receipts" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (email.isBlank()) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "addresses_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Delivery Addresses (${addresses.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pre-filled automatically during basket checkout.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showAddAddressDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("add_buyer_address_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Address")
                    }
                }
            }

            if (addresses.isEmpty()) {
                item(key = "addresses_empty") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Save Home or Work addresses for 1-tap puja & grocery checkout.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(addresses, key = { it.id }) { addr ->
                    AddressProfileCard(
                        address = addr,
                        onSetDefault = { viewModel.setDefaultAddress(addr.id) },
                        onEdit = { editingAddress = addr },
                        onDelete = { viewModel.deleteAddress(addr.id) }
                    )
                }
            }

            item(key = "payments_header") {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Preferred Payment Methods (${paymentMethods.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Save UPI handle, Cash on Delivery, or masked label.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showAddPaymentDialog = true },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("add_payment_method_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Method")
                    }
                }
            }

            if (paymentMethods.isEmpty()) {
                item(key = "payments_empty") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Payments,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Add your preferred UPI ID or Cash on Delivery option for faster orders.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(paymentMethods, key = { it.id }) { method ->
                    PaymentMethodProfileCard(
                        method = method,
                        onSetDefault = { viewModel.setDefaultPaymentMethod(method.id) },
                        onEdit = { editingPayment = method },
                        onDelete = { viewModel.deletePaymentMethod(method.id) }
                    )
                }
            }
        }
    }

    if (showEditContactDialog) {
        EditContactDialog(
            initialName = displayName,
            initialPhone = phoneNumber,
            initialEmail = email,
            onDismiss = { showEditContactDialog = false },
            onSave = { newName, newPhone, newEmail ->
                saveAccountDetails(
                    context = context,
                    uid = viewModel.buyerId,
                    details = AccountContactDetails(
                        displayName = newName,
                        phoneNumber = newPhone,
                        email = newEmail
                    )
                )
                viewModel.updateContactDetails(
                    displayName = newName,
                    email = newEmail,
                    phoneNumber = newPhone
                )
                showEditContactDialog = false
            }
        )
    }

    if (showAddAddressDialog || editingAddress != null) {
        val current = editingAddress
        AddressEditorDialog(
            initial = current,
            defaultRecipient = displayName,
            defaultPhone = phoneNumber,
            onDismiss = {
                showAddAddressDialog = false
                editingAddress = null
            },
            onSave = { label, recipient, phone, street, neighborhood, landmark, makeDefault ->
                viewModel.saveAddress(
                    addressId = current?.id,
                    label = label,
                    recipientName = recipient,
                    phoneNumber = phone,
                    streetAddress = street,
                    neighborhood = neighborhood,
                    landmark = landmark,
                    makeDefault = makeDefault
                )
                showAddAddressDialog = false
                editingAddress = null
            }
        )
    }

    if (showAddPaymentDialog || editingPayment != null) {
        val current = editingPayment
        PaymentMethodEditorDialog(
            initial = current,
            onDismiss = {
                showAddPaymentDialog = false
                editingPayment = null
            },
            onSave = { type, label, hint, makeDefault ->
                viewModel.savePaymentMethod(
                    methodId = current?.id,
                    methodType = type,
                    displayLabel = label,
                    handleOrHint = hint,
                    makeDefault = makeDefault
                )
                showAddPaymentDialog = false
                editingPayment = null
            }
        )
    }
}

@Composable
private fun AddressProfileCard(
    address: BuyerAddress,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val labelIcon = when {
        address.label.equals("Work", ignoreCase = true) -> Icons.Filled.Work
        address.label.equals("Home", ignoreCase = true) -> Icons.Filled.Home
        else -> Icons.Filled.LocationOn
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("buyer_address_card_${address.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (address.defaultAddress) {
            BorderStroke(1.2.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
        } else null
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = labelIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = address.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (address.defaultAddress) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "DEFAULT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${address.label} address")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete ${address.label} address",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Text(
                text = "${address.recipientName} • ${address.phoneNumber}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = address.formattedDeliverySummary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!address.defaultAddress) {
                TextButton(
                    onClick = onSetDefault,
                    modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Set as Default Delivery Address")
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodProfileCard(
    method: PreferredPaymentMethod,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val type = method.methodTypeEnum()
    val icon = when (type) {
        PaymentMethodType.UPI -> Icons.Filled.QrCode2
        PaymentMethodType.COD -> Icons.Filled.Payments
        PaymentMethodType.CARD_LABEL -> Icons.Filled.CreditCard
        PaymentMethodType.WALLET -> Icons.Filled.AccountBalanceWallet
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("buyer_payment_card_${method.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (method.defaultMethod) {
            BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        } else null
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = type.label,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = method.displayLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (method.defaultMethod) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "DEFAULT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (method.handleOrHint.isNotBlank()) {
                                "${type.label} • ${method.handleOrHint}"
                            } else {
                                type.label
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${method.displayLabel}")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete ${method.displayLabel}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (!method.defaultMethod) {
                TextButton(
                    onClick = onSetDefault,
                    modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Set as Default Payment")
                }
            }
        }
    }
}

@Composable
private fun EditContactDialog(
    initialName: String,
    initialPhone: String,
    initialEmail: String,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, email: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var email by remember { mutableStateOf(initialEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Contact Details") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_edit_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Number") },
                    placeholder = { Text("+91 9876543210") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_edit_phone_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email ID") },
                    placeholder = { Text("you@example.com") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_edit_email_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), phone.trim(), email.trim()) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.testTag("profile_save_contact_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddressEditorDialog(
    initial: BuyerAddress?,
    defaultRecipient: String,
    defaultPhone: String,
    onDismiss: () -> Unit,
    onSave: (
        label: String,
        recipientName: String,
        phoneNumber: String,
        streetAddress: String,
        neighborhood: String,
        landmark: String,
        makeDefault: Boolean
    ) -> Unit
) {
    val labelPresets = listOf("Home", "Work", "Other")
    var label by remember { mutableStateOf(initial?.label ?: "Home") }
    var recipient by remember { mutableStateOf(initial?.recipientName ?: defaultRecipient) }
    var phone by remember { mutableStateOf(initial?.phoneNumber ?: defaultPhone) }
    var street by remember { mutableStateOf(initial?.streetAddress ?: "") }
    var neighborhood by remember { mutableStateOf(initial?.neighborhood ?: "") }
    var landmark by remember { mutableStateOf(initial?.landmark ?: "") }
    var makeDefault by remember { mutableStateOf(initial?.defaultAddress ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Delivery Address" else "Edit Delivery Address") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labelPresets.forEach { preset ->
                        FilterChip(
                            selected = label.equals(preset, ignoreCase = true),
                            onClick = { label = preset },
                            label = { Text(preset) }
                        )
                    }
                }
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("Recipient Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = street,
                    onValueChange = { street = it },
                    label = { Text("House / Flat / Street Address") },
                    placeholder = { Text("e.g. #42, 12th Main Road") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_street_input")
                )
                OutlinedTextField(
                    value = neighborhood,
                    onValueChange = { neighborhood = it },
                    label = { Text("Neighborhood / Area") },
                    placeholder = { Text("e.g. Indiranagar, Bengaluru") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address_neighborhood_input")
                )
                OutlinedTextField(
                    value = landmark,
                    onValueChange = { landmark = it },
                    label = { Text("Landmark (optional)") },
                    placeholder = { Text("e.g. Near Metro Station") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = makeDefault,
                        onCheckedChange = { makeDefault = it }
                    )
                    Text("Use as default delivery address", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        label,
                        recipient,
                        phone,
                        street,
                        neighborhood,
                        landmark,
                        makeDefault
                    )
                },
                enabled = street.trim().isNotEmpty() && neighborhood.trim().isNotEmpty(),
                modifier = Modifier.testTag("address_save_button")
            ) {
                Text("Save Address")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PaymentMethodEditorDialog(
    initial: PreferredPaymentMethod?,
    onDismiss: () -> Unit,
    onSave: (
        methodType: PaymentMethodType,
        displayLabel: String,
        handleOrHint: String,
        makeDefault: Boolean
    ) -> Unit
) {
    var selectedType by remember { mutableStateOf(initial?.methodTypeEnum() ?: PaymentMethodType.UPI) }
    var displayLabel by remember { mutableStateOf(initial?.displayLabel ?: "") }
    var handleOrHint by remember { mutableStateOf(initial?.handleOrHint ?: "") }
    var makeDefault by remember { mutableStateOf(initial?.defaultMethod ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Payment Preference" else "Edit Payment Preference") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Select payment type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PaymentMethodType.entries.chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { type ->
                                FilterChip(
                                    selected = selectedType == type,
                                    onClick = {
                                        selectedType = type
                                        if (displayLabel.isBlank()) {
                                            displayLabel = when (type) {
                                                PaymentMethodType.UPI -> "Primary UPI"
                                                PaymentMethodType.COD -> "Cash on Delivery"
                                                PaymentMethodType.CARD_LABEL -> "Saved Card"
                                                PaymentMethodType.WALLET -> "Digital Wallet"
                                            }
                                        }
                                    },
                                    label = { Text(type.label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = displayLabel,
                    onValueChange = { displayLabel = it },
                    label = { Text("Label") },
                    placeholder = { Text("e.g. GPay Personal or Cash at Door") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_label_input")
                )
                OutlinedTextField(
                    value = handleOrHint,
                    onValueChange = { handleOrHint = it },
                    label = {
                        Text(
                            when (selectedType) {
                                PaymentMethodType.UPI -> "UPI ID (e.g. priya@okaxis)"
                                PaymentMethodType.COD -> "Delivery Note (e.g. Keep UPI QR ready)"
                                PaymentMethodType.CARD_LABEL -> "Masked Label (e.g. Bank •••• 4821)"
                                PaymentMethodType.WALLET -> "Wallet Name (e.g. PhonePe Wallet)"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_hint_input")
                )
                Text(
                    text = "For security, store only a UPI ID or masked label — never full card numbers or PINs.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = makeDefault,
                        onCheckedChange = { makeDefault = it }
                    )
                    Text("Set as default payment preference", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        selectedType,
                        displayLabel.ifBlank { selectedType.label },
                        handleOrHint,
                        makeDefault
                    )
                },
                modifier = Modifier.testTag("payment_save_button")
            ) {
                Text("Save Method")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
