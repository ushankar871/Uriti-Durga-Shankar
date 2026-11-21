package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.GeminiAssistantService
import com.example.auth.authStateFlow
import com.example.auth.loadAccountDetails
import com.example.auth.loadSavedPortalRole
import com.example.auth.savePortalRole
import com.example.auth.signOut
import com.example.data.SellerRepository
import com.example.firebase.configureAppCheck
import com.example.model.AccountRole
import com.example.model.UiState
import com.example.notifications.LowStockNotifier
import com.example.ui.BuyerViewModel
import com.example.ui.SellerViewModel
import com.example.ui.StockFilter
import com.example.ui.components.BuyerCartSheet
import com.example.ui.components.LiveDeliveryMapSheet
import com.example.ui.components.OtpChallengeDialog
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BuyerOrdersScreen
import com.example.ui.screens.BuyerProfileScreen
import com.example.ui.screens.BuyerShopScreen
import com.example.ui.screens.BuyerStoreInfoScreen
import com.example.ui.screens.BuyerWishlistScreen
import com.example.ui.screens.SellerHubScreen
import com.example.ui.screens.StockDashboardScreen
import com.example.ui.screens.StoreMapScreen
import com.example.ui.theme.ShubhMartTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

sealed class SellerRoute(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object StockBoard : SellerRoute("stock_board", R.string.nav_stock_board, Icons.Filled.Inventory2, Icons.Outlined.Inventory2)
    data object SellerHub : SellerRoute("seller_hub", R.string.nav_seller_hub, Icons.Filled.Storefront, Icons.Outlined.Storefront)
    data object StoreMap : SellerRoute("store_map", R.string.nav_store_map, Icons.Filled.Map, Icons.Outlined.Map)
    data object AiAssistant : SellerRoute("ai_assistant", R.string.nav_ai_assistant, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)

    companion object {
        val all = listOf(StockBoard, SellerHub, StoreMap, AiAssistant)
    }
}

sealed class BuyerRoute(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Shop : BuyerRoute("buyer_shop", R.string.nav_buyer_shop, Icons.Filled.ShoppingBag, Icons.Outlined.ShoppingBag)
    data object Wishlist : BuyerRoute("buyer_wishlist", R.string.nav_buyer_wishlist, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder)
    data object Orders : BuyerRoute("buyer_orders", R.string.nav_buyer_orders, Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong)
    data object StoreInfo : BuyerRoute("buyer_store", R.string.nav_buyer_store, Icons.Filled.Map, Icons.Outlined.Map)
    data object Profile : BuyerRoute("buyer_profile", R.string.nav_buyer_profile, Icons.Filled.Person, Icons.Outlined.Person)

    companion object {
        val all = listOf(Shop, Wishlist, Orders, StoreInfo, Profile)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            ShubhMartTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    var activeRole by remember(user?.uid) {
        mutableStateOf(
            if (user != null) loadSavedPortalRole(context, user.uid) else null
        )
    }

    val savedContactDetails = remember(user?.uid, activeRole) {
        if (user != null) loadAccountDetails(context, user.uid) else com.example.auth.AccountContactDetails()
    }
    val resolvedDisplayName = remember(user?.uid, activeRole, savedContactDetails) {
        savedContactDetails.displayName.ifBlank {
            user?.displayName ?: user?.email ?: activeRole?.title ?: "User"
        }
    }
    val resolvedEmail = remember(user?.uid, activeRole, savedContactDetails) {
        savedContactDetails.email.ifBlank { user?.email ?: "" }
    }

    LaunchedEffect(user?.uid, activeRole, savedContactDetails) {
        val role = activeRole
        if (user != null && role != null) {
            val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
            SellerRepository(db).upsertAccountProfile(
                userId = user.uid,
                role = role,
                displayName = resolvedDisplayName,
                email = resolvedEmail,
                phoneNumber = savedContactDetails.phoneNumber
            )
        }
    }

    if (user == null || activeRole == null) {
        AuthScreen(
            onPortalAuthenticated = { chosenRole ->
                activeRole = chosenRole
            }
        )
    } else {
        key("${user.uid}_${activeRole!!.name}") {
            when (activeRole!!) {
                AccountRole.SELLER -> SellerAppShell(
                    sellerId = user.uid,
                    userDisplayName = resolvedDisplayName,
                    onSwitchRole = { targetRole ->
                        savePortalRole(context, user.uid, targetRole)
                        activeRole = targetRole
                    },
                    onSignedOut = { activeRole = null }
                )

                AccountRole.BUYER -> BuyerAppShell(
                    buyerId = user.uid,
                    userDisplayName = resolvedDisplayName,
                    onSwitchRole = { targetRole ->
                        savePortalRole(context, user.uid, targetRole)
                        activeRole = targetRole
                    },
                    onSignedOut = { activeRole = null }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SellerAppShell(
    sellerId: String,
    userDisplayName: String,
    onSwitchRole: (AccountRole) -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: SellerViewModel = viewModel(
        key = "seller_$sellerId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SellerViewModel(
                    repository = SellerRepository(db),
                    aiService = GeminiAssistantService(),
                    sellerId = sellerId
                )
            }
        }
    )

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val pendingOtp by viewModel.pendingOtpAction.collectAsStateWithLifecycle()
    val otpSessionExpires by viewModel.otpSessionExpiresAtMillis.collectAsStateWithLifecycle()
    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val storeConfigState by viewModel.storeConfigState.collectAsStateWithLifecycle()

    val products = (productsState as? UiState.Success)?.data ?: emptyList()
    val storeConfig = (storeConfigState as? UiState.Success)?.data
    val storeName = storeConfig?.storeName ?: "Lakshmi Store"
    val lowStockThreshold = (storeConfig?.lowStockThreshold ?: 5L).coerceIn(1L, 1000L)
    val lowStockAlertsEnabled = storeConfig?.lowStockAlertsEnabled ?: true
    val lowStockItems = remember(products, lowStockThreshold) {
        products.filter { it.isLowStock(lowStockThreshold) }
            .sortedWith(compareBy<com.example.model.Product> { it.stockQty }.thenBy { it.name.lowercase() })
    }

    var hasNotificationPermission by remember {
        mutableStateOf(LowStockNotifier.hasNotificationPermission(context))
    }
    var lastNotifiedSignature by remember { mutableStateOf<String?>(null) }
    var hasInitializedLowStockSnapshot by remember { mutableStateOf(false) }
    var previousLowStockKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    LaunchedEffect(sellerId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(productsState, lowStockThreshold, lowStockAlertsEnabled, hasNotificationPermission, storeName) {
        if (productsState !is UiState.Success) return@LaunchedEffect
        val currentLowKeys = lowStockItems.map { "${it.id}:${!it.inStock || it.stockQty <= 0L}" }.toSet()

        if (!lowStockAlertsEnabled || lowStockItems.isEmpty()) {
            LowStockNotifier.cancelLowStockAlert(context)
            lastNotifiedSignature = null
            previousLowStockKeys = emptySet()
            hasInitializedLowStockSnapshot = true
            return@LaunchedEffect
        }

        val hasNewLowStockAlert = hasInitializedLowStockSnapshot && currentLowKeys.any { it !in previousLowStockKeys }
        previousLowStockKeys = currentLowKeys
        hasInitializedLowStockSnapshot = true

        val signature = "$lowStockThreshold|" +
            lowStockItems.sortedBy { it.id }.joinToString(",") { "${it.id}:${it.stockQty}:${it.inStock}" }

        val shouldPlayChime = hasNewLowStockAlert && LowStockNotifier.isAlertSoundEnabled(context, sellerId)
        if (signature != lastNotifiedSignature) {
            if (hasNotificationPermission) {
                val posted = LowStockNotifier.showLowStockAlert(
                    context = context,
                    storeName = storeName,
                    lowStockItems = lowStockItems,
                    threshold = lowStockThreshold,
                    playChime = shouldPlayChime
                )
                if (posted) {
                    lastNotifiedSignature = signature
                }
            } else {
                if (shouldPlayChime) {
                    LowStockNotifier.playSubtleAlertChime()
                }
                lastNotifiedSignature = signature
            }
        }
    }

    var otpSessionSecondsLeft by remember { mutableLongStateOf(0L) }
    LaunchedEffect(otpSessionExpires) {
        while (otpSessionExpires > System.currentTimeMillis()) {
            otpSessionSecondsLeft = (otpSessionExpires - System.currentTimeMillis()) / 1000L
            delay(1000L)
        }
        otpSessionSecondsLeft = 0L
    }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    val isExpandedScreen = LocalConfiguration.current.screenWidthDp >= 600

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = stringResource(id = R.string.app_name), maxLines = 1)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Seller",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (lowStockAlertsEnabled && lowStockItems.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .clickable {
                                    viewModel.setStockFilter(StockFilter.LOW_STOCK_ONLY)
                                    navController.navigate(SellerRoute.StockBoard.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                .padding(end = 6.dp)
                                .testTag("topbar_low_stock_alert_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = "Low inventory alerts",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${lowStockItems.size} Low",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    if (otpSessionSecondsLeft > 0L) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier
                                .clickable { viewModel.lockOtpSessionNow() }
                                .padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LockOpen,
                                    contentDescription = "Lock OTP session",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${otpSessionSecondsLeft}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            viewModel.stopSpeaking()
                            viewModel.stopLiveVoice()
                            viewModel.stopLiveLocationBroadcast()
                            onSwitchRole(AccountRole.BUYER)
                        },
                        modifier = Modifier.testTag("switch_to_buyer_role_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = stringResource(id = R.string.switch_to_buyer_role)
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.stopSpeaking()
                            viewModel.stopLiveVoice()
                            viewModel.stopLiveLocationBroadcast()
                            signOut(context = context, credentialManager = credentialManager, scope = scope) {
                                onSignedOut()
                            }
                        },
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out Seller ($userDisplayName)"
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!isExpandedScreen) {
                NavigationBar {
                    SellerRoute.all.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(id = dest.labelRes)
                                )
                            },
                            label = { Text(stringResource(id = dest.labelRes)) },
                            modifier = Modifier.testTag("nav_${dest.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isExpandedScreen) {
                NavigationRail {
                    SellerRoute.all.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(id = dest.labelRes)
                                )
                            },
                            label = { Text(stringResource(id = dest.labelRes)) }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                NavHost(
                    navController = navController,
                    startDestination = SellerRoute.StockBoard.route
                ) {
                    composable(SellerRoute.StockBoard.route) {
                        StockDashboardScreen(
                            viewModel = viewModel,
                            onOpenSellerInventory = {
                                navController.navigate(SellerRoute.SellerHub.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    composable(SellerRoute.SellerHub.route) {
                        SellerHubScreen(viewModel = viewModel)
                    }
                    composable(SellerRoute.StoreMap.route) {
                        StoreMapScreen(viewModel = viewModel)
                    }
                    composable(SellerRoute.AiAssistant.route) {
                        AiAssistantScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    pendingOtp?.let { challengeAction ->
        OtpChallengeDialog(
            uid = sellerId,
            actionLabel = challengeAction.actionLabel,
            isDestructive = challengeAction.isDestructive,
            onDismiss = { viewModel.dismissOtpChallenge() },
            onVerified = { keepTwoMin -> viewModel.onOtpVerified(keepTwoMin) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuyerAppShell(
    buyerId: String,
    userDisplayName: String,
    onSwitchRole: (AccountRole) -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: BuyerViewModel = viewModel(
        key = "buyer_$buyerId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BuyerViewModel(
                    repository = SellerRepository(db),
                    aiService = GeminiAssistantService(),
                    buyerId = buyerId,
                    buyerDisplayName = userDisplayName
                )
            }
        }
    )

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val pendingOtp by viewModel.pendingOtpAction.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val activeStore by viewModel.activeStore.collectAsStateWithLifecycle()
    val addressesState by viewModel.addressesState.collectAsStateWithLifecycle()
    val paymentMethodsState by viewModel.paymentMethodsState.collectAsStateWithLifecycle()
    val savedAddresses = (addressesState as? UiState.Success)?.data ?: emptyList()
    val savedPaymentMethods = (paymentMethodsState as? UiState.Success)?.data ?: emptyList()
    var showCartSheet by remember { mutableStateOf(false) }
    var trackingOrderId by remember { mutableStateOf<String?>(null) }

    val totalCartCount = cartItems.values.sumOf { it.quantity }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    val isExpandedScreen = LocalConfiguration.current.screenWidthDp >= 600

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = stringResource(id = R.string.app_name), maxLines = 1)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "Buyer",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCartSheet = true },
                        modifier = Modifier.testTag("buyer_topbar_cart_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (totalCartCount > 0) {
                                    Badge {
                                        Text(
                                            text = if (totalCartCount > 99) "99+" else totalCartCount.toString()
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ShoppingCart,
                                contentDescription = "Open Cart ($totalCartCount items)"
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            viewModel.stopSpeaking()
                            onSwitchRole(AccountRole.SELLER)
                        },
                        modifier = Modifier.testTag("switch_to_seller_role_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = stringResource(id = R.string.switch_to_seller_role)
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.stopSpeaking()
                            signOut(context = context, credentialManager = credentialManager, scope = scope) {
                                onSignedOut()
                            }
                        },
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out Buyer ($userDisplayName)"
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!isExpandedScreen) {
                NavigationBar {
                    BuyerRoute.all.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(id = dest.labelRes)
                                )
                            },
                            label = { Text(stringResource(id = dest.labelRes)) },
                            modifier = Modifier.testTag("nav_${dest.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isExpandedScreen) {
                NavigationRail {
                    BuyerRoute.all.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(id = dest.labelRes)
                                )
                            },
                            label = { Text(stringResource(id = dest.labelRes)) }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                NavHost(
                    navController = navController,
                    startDestination = BuyerRoute.Shop.route
                ) {
                    composable(BuyerRoute.Shop.route) {
                        BuyerShopScreen(
                            viewModel = viewModel,
                            onOpenCart = { showCartSheet = true },
                            onOpenOrders = {
                                navController.navigate(BuyerRoute.Orders.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onTrackOrder = { orderId -> trackingOrderId = orderId },
                            onSwitchToSellerLogin = {
                                viewModel.stopSpeaking()
                                onSwitchRole(AccountRole.SELLER)
                            }
                        )
                    }
                    composable(BuyerRoute.Wishlist.route) {
                        BuyerWishlistScreen(
                            viewModel = viewModel,
                            onOpenShop = {
                                navController.navigate(BuyerRoute.Shop.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenCart = { showCartSheet = true }
                        )
                    }
                    composable(BuyerRoute.Orders.route) {
                        BuyerOrdersScreen(
                            viewModel = viewModel,
                            onOpenShop = {
                                navController.navigate(BuyerRoute.Shop.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenStoreReviews = {
                                navController.navigate(BuyerRoute.StoreInfo.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onTrackOrder = { orderId -> trackingOrderId = orderId }
                        )
                    }
                    composable(BuyerRoute.StoreInfo.route) {
                        BuyerStoreInfoScreen(
                            viewModel = viewModel,
                            onTrackOrder = { orderId -> trackingOrderId = orderId }
                        )
                    }
                    composable(BuyerRoute.Profile.route) {
                        BuyerProfileScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    if (showCartSheet) {
        BuyerCartSheet(
            store = activeStore,
            defaultName = viewModel.buyerDisplayName,
            cartItems = cartItems.values.toList(),
            savedAddresses = savedAddresses,
            paymentMethods = savedPaymentMethods,
            onDismiss = { showCartSheet = false },
            onAddQty = { product -> viewModel.addToCart(product) },
            onRemoveQty = { product -> viewModel.removeFromCart(product) },
            onDeleteLineItem = { productId -> viewModel.deleteFromCart(productId) },
            onClearCart = {
                viewModel.clearCart()
                showCartSheet = false
            },
            onBrowseShop = {
                navController.navigate(BuyerRoute.Shop.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onPlaceOrder = { name, neighborhood ->
                val targetStore = activeStore
                if (targetStore != null) {
                    val matchedAddress = savedAddresses.firstOrNull {
                        it.formattedDeliverySummary() == neighborhood || it.neighborhood == neighborhood
                    } ?: savedAddresses.firstOrNull { it.defaultAddress }
                    viewModel.placeOrder(
                        customerName = name,
                        neighborhood = neighborhood,
                        lat = matchedAddress?.lat ?: (targetStore.storeLat + 0.008),
                        lng = matchedAddress?.lng ?: (targetStore.storeLng + 0.008)
                    )
                    showCartSheet = false
                }
            }
        )
    }

    trackingOrderId?.let { activeTrackingId ->
        LiveDeliveryMapSheet(
            orderId = activeTrackingId,
            viewModel = viewModel,
            onDismiss = { trackingOrderId = null }
        )
    }

    pendingOtp?.let { challengeAction ->
        OtpChallengeDialog(
            uid = buyerId,
            actionLabel = challengeAction.actionLabel,
            isDestructive = challengeAction.isDestructive,
            onDismiss = { viewModel.dismissOtpChallenge() },
            onVerified = { _ -> viewModel.onOtpVerified() }
        )
    }
}
