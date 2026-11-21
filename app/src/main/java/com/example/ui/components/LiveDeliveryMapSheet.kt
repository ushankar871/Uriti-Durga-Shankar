package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.model.OrderStatus
import com.example.model.SellerOrder
import com.example.model.UiState
import com.example.ui.BuyerViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.firebase.Timestamp
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDeliveryMapSheet(
    orderId: String,
    viewModel: BuyerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ordersState by viewModel.buyerOrdersState.collectAsStateWithLifecycle()
    val storesState by viewModel.storesState.collectAsStateWithLifecycle()

    val orders = (ordersState as? UiState.Success)?.data ?: emptyList()
    val stores = (storesState as? UiState.Success)?.data ?: emptyList()
    val order = orders.firstOrNull { it.id == orderId } ?: return
    val store = stores.firstOrNull { it.userId == order.sellerId }

    val status = order.statusEnum()
    val storePos = remember(store?.storeLat, store?.storeLng, order.lat, order.lng) {
        LatLng(store?.storeLat ?: order.lat, store?.storeLng ?: order.lng)
    }
    val destPos = remember(order.lat, order.lng) {
        LatLng(order.lat, order.lng)
    }
    val liveOrderPos = remember(
        order.riderLat,
        order.riderLng,
        status,
        storePos.latitude,
        storePos.longitude,
        destPos.latitude,
        destPos.longitude
    ) {
        resolveLiveOrderLatLng(order = order, storePos = storePos, destPos = destPos)
    }

    val distanceRemainingKm = remember(liveOrderPos, destPos) {
        haversineKm(
            liveOrderPos.latitude,
            liveOrderPos.longitude,
            destPos.latitude,
            destPos.longitude
        )
    }
    val etaLabel = remember(status, distanceRemainingKm) {
        when {
            status == OrderStatus.DELIVERED -> "Delivered"
            status == OrderStatus.PENDING -> "Preparing at store"
            status == OrderStatus.PACKED -> "Packed • Awaiting pickup"
            distanceRemainingKm < 0.08 -> "Arriving now"
            else -> {
                val mins = ((distanceRemainingKm / 22.0) * 60.0).roundToInt().coerceIn(2, 90)
                "ETA ~$mins min"
            }
        }
    }
    val distanceLabel = remember(status, distanceRemainingKm) {
        when {
            status == OrderStatus.DELIVERED -> "At delivery address"
            distanceRemainingKm < 1.0 -> "${(distanceRemainingKm * 1000).roundToInt()} m away"
            else -> String.format(Locale.US, "%.1f km away", distanceRemainingKm)
        }
    }
    val shortRef = remember(order.id) {
        order.id.takeLast(6).uppercase(Locale.US).ifBlank { "ORDER" }
    }
    val lastPingLabel = remember(order.riderUpdatedAt, order.updatedAt) {
        formatLivePingTimestamp(order.riderUpdatedAt ?: order.updatedAt)
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms.values.any { it }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(liveOrderPos, 13.5f)
    }
    val storeMarkerState = remember(orderId) { MarkerState(position = storePos) }
    val courierMarkerState = remember(orderId) { MarkerState(position = liveOrderPos) }
    val destMarkerState = remember(orderId) { MarkerState(position = destPos) }

    LaunchedEffect(storePos) {
        storeMarkerState.position = storePos
    }
    LaunchedEffect(destPos) {
        destMarkerState.position = destPos
    }
    LaunchedEffect(liveOrderPos) {
        courierMarkerState.position = liveOrderPos
    }

    suspend fun fitRouteBounds() {
        val points = listOf(storePos, liveOrderPos, destPos).distinct()
        if (points.size <= 1) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(liveOrderPos, 14f))
        } else {
            val builder = LatLngBounds.Builder()
            points.forEach { builder.include(it) }
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(builder.build(), 110)
                )
            } catch (_: Exception) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(liveOrderPos, 13.5f))
            }
        }
    }

    LaunchedEffect(orderId, liveOrderPos) {
        fitRouteBounds()
    }

    val pulseTransition = rememberInfiniteTransition(label = "live_tracking_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val mapsKeyMissing = BuildConfig.MAPS_API_KEY.isBlank() || BuildConfig.MAPS_API_KEY == "MY_MAPS_API_KEY"
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isMapGestureEnabled by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Live Order Tracking • #$shortRef",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (status == OrderStatus.DELIVERED) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (status != OrderStatus.DELIVERED) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .alpha(pulseAlpha)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                                Text(
                                    text = if (status == OrderStatus.DELIVERED) "DELIVERED" else "LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (status == OrderStatus.DELIVERED) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = "${order.storeName.ifBlank { "Lakshmi Store" }} -> ${order.neighborhood}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_live_delivery_map_button")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close live tracking map")
                }
            }

            if (mapsKeyMissing) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Add MAPS_API_KEY in AI Studio Secrets to render Google Maps map tiles alongside live GPS coordinates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Live ETA & Distance Telemetry Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_tracking_telemetry_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (status == OrderStatus.DELIVERED) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Filled.LocalShipping
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text(
                                text = etaLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "$distanceLabel • ${if (order.hasLiveRiderCoordinates()) "Live GPS signal" else "Route estimate"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = lastPingLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Google Maps SDK Real-Time Map Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .testTag("live_delivery_google_map")
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        myLocationButtonEnabled = false,
                        scrollGesturesEnabled = isMapGestureEnabled,
                        zoomGesturesEnabled = isMapGestureEnabled,
                        rotationGesturesEnabled = isMapGestureEnabled,
                        tiltGesturesEnabled = isMapGestureEnabled
                    )
                ) {
                    // Store Origin Marker
                    Marker(
                        state = storeMarkerState,
                        title = order.storeName.ifBlank { "Lakshmi Store" },
                        snippet = store?.address ?: "Dispatch Store",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                    )

                    // Completed Route Leg (Store -> Courier)
                    if (liveOrderPos != storePos) {
                        Polyline(
                            points = listOf(storePos, liveOrderPos),
                            color = Color(0xFF1B6B46),
                            width = 9f
                        )
                    }

                    // Remaining Route Leg (Courier -> Buyer Destination)
                    if (liveOrderPos != destPos) {
                        Polyline(
                            points = listOf(liveOrderPos, destPos),
                            color = Color(0xFFB83B1D),
                            width = 8f,
                            pattern = listOf(Dash(22f), Gap(14f))
                        )
                    }

                    // Live Order / Courier Pin + GPS Radius
                    Marker(
                        state = courierMarkerState,
                        title = if (status == OrderStatus.DELIVERED) "Delivered Order #$shortRef" else "Order #$shortRef (${status.label})",
                        snippet = "$etaLabel • $distanceLabel",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                    if (status != OrderStatus.DELIVERED) {
                        Circle(
                            center = liveOrderPos,
                            radius = 160.0,
                            fillColor = Color(0x281E88E5),
                            strokeColor = Color(0xFF1E88E5),
                            strokeWidth = 3f
                        )
                    }

                    // Buyer Destination Marker
                    Marker(
                        state = destMarkerState,
                        title = "Delivery Pin (${order.customerName})",
                        snippet = order.neighborhood,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }

                // Floating map controls
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { fitRouteBounds() } },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("recenter_live_map_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CenterFocusStrong,
                            contentDescription = "Fit route on map"
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            if (!hasLocationPermission) {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(destPos, 15f)
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = "Focus delivery pin / enable my location"
                        )
                    }
                }

                // Scroll vs Pan Map Gesture Toggle Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isMapGestureEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    },
                    shadowElevation = 3.dp,
                    onClick = { isMapGestureEnabled = !isMapGestureEnabled },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .testTag("toggle_live_map_gestures_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isMapGestureEnabled) Icons.Filled.PanTool else Icons.Filled.SwipeVertical,
                            contentDescription = null,
                            tint = if (isMapGestureEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isMapGestureEnabled) "Panning Map (Tap to Scroll)" else "Move Map",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMapGestureEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            // Legend + Live Coordinates Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendChip(color = Color(0xFFEF6C00), label = "Store", icon = Icons.Filled.Storefront)
                LegendChip(color = Color(0xFF1E88E5), label = "Live Order", icon = Icons.Filled.LocalShipping)
                LegendChip(color = Color(0xFF2E7D32), label = "Drop Pin", icon = Icons.Filled.LocationOn)
            }

            Text(
                text = "Courier Pin: ${String.format(Locale.US, "%.4f, %.4f", liveOrderPos.latitude, liveOrderPos.longitude)} • Drop Pin: ${String.format(Locale.US, "%.4f, %.4f", destPos.latitude, destPos.longitude)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Smooth Animated Order Stage Progress (Ordered -> Packed -> Out for Delivery -> Delivered)
            AnimatedOrderProgressTracker(
                currentStatus = status,
                compact = false
            )

            if (status != OrderStatus.DELIVERED && order.deliveryOtp.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Password,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Share Handoff Delivery OTP",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = order.deliveryOtp,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (status == OrderStatus.DELIVERED && order.deliveryMethod.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
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
                                imageVector = if (order.deliveryMethod == "PHOTO") {
                                    Icons.Filled.PhotoCamera
                                } else {
                                    Icons.Filled.VerifiedUser
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (order.deliveryMethod == "PHOTO") {
                                    "Delivery confirmed via photograph"
                                } else {
                                    "Delivery confirmed via Buyer OTP"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        if (order.deliveryProofUri.isNotBlank()) {
                            AsyncImage(
                                model = order.deliveryProofUri,
                                contentDescription = "Delivery confirmation photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendChip(
    color: Color,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun resolveLiveOrderLatLng(
    order: SellerOrder,
    storePos: LatLng,
    destPos: LatLng
): LatLng {
    val liveLat = order.riderLat
    val liveLng = order.riderLng
    if (liveLat != null && liveLng != null) {
        return LatLng(liveLat, liveLng)
    }
    return when (order.statusEnum()) {
        OrderStatus.PENDING, OrderStatus.PACKED -> storePos
        OrderStatus.OUT_FOR_DELIVERY -> LatLng(
            storePos.latitude + (destPos.latitude - storePos.latitude) * 0.30,
            storePos.longitude + (destPos.longitude - storePos.longitude) * 0.30
        )
        OrderStatus.DELIVERED -> destPos
    }
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun formatLivePingTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return "Just now"
    val formatter = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
    return formatter.format(timestamp.toDate())
}
