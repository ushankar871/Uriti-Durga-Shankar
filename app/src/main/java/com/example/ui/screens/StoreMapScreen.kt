package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.model.UiState
import com.example.ui.SellerViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
@Composable
fun StoreMapScreen(
    viewModel: SellerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storeConfigState by viewModel.storeConfigState.collectAsStateWithLifecycle()
    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()

    val savedConfig = (storeConfigState as? UiState.Success)?.data
    val orders = (ordersState as? UiState.Success)?.data ?: emptyList()

    var storeName by remember { mutableStateOf("Lakshmi Puja, Fancy & Grocery") }
    var address by remember { mutableStateOf("100 Feet Rd, Indiranagar, Bengaluru") }
    var storeLat by remember { mutableDoubleStateOf(12.9716) }
    var storeLng by remember { mutableDoubleStateOf(77.5946) }
    var radiusKm by remember { mutableDoubleStateOf(5.0) }
    var locationNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(savedConfig) {
        if (savedConfig != null) {
            storeName = savedConfig.storeName
            address = savedConfig.address
            storeLat = savedConfig.storeLat
            storeLng = savedConfig.storeLng
            radiusKm = savedConfig.deliveryRadiusKm
        }
    }

    val storePosition = LatLng(storeLat, storeLng)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(storePosition, 12.5f)
    }

    LaunchedEffect(storeLat, storeLng) {
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(storeLat, storeLng), 12.5f)
        )
    }

    val fusedLocationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    storeLat = loc.latitude
                    storeLng = loc.longitude
                    locationNotice = "Pinned store to your current location."
                } else {
                    locationNotice = "Location unavailable right now; tap on the map to place pin."
                }
            }
        } else {
            locationNotice = "Location permission denied; tap directly on the map to position your store."
        }
    }

    val mapsKeyMissing = BuildConfig.MAPS_API_KEY.isBlank() || BuildConfig.MAPS_API_KEY == "MY_MAPS_API_KEY"
    var isMapGestureEnabled by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Store Location & Delivery Zone",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            if (mapsKeyMissing) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add MAPS_API_KEY in AI Studio Secrets to render live Google Maps tiles. Tap the map or adjust coordinates below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        scrollGesturesEnabled = isMapGestureEnabled,
                        zoomGesturesEnabled = isMapGestureEnabled,
                        rotationGesturesEnabled = isMapGestureEnabled,
                        tiltGesturesEnabled = isMapGestureEnabled
                    ),
                    onMapClick = { latLng ->
                        storeLat = latLng.latitude
                        storeLng = latLng.longitude
                        locationNotice = "Moved store pin to ${String.format("%.4f, %.4f", storeLat, storeLng)}"
                    }
                ) {
                    Marker(
                        state = MarkerState(position = storePosition),
                        title = storeName,
                        snippet = "$address (${radiusKm.roundToInt()} km delivery)"
                    )

                    Circle(
                        center = storePosition,
                        radius = radiusKm * 1000.0,
                        fillColor = Color(0x28B83B1D),
                        strokeColor = Color(0xFFB83B1D),
                        strokeWidth = 4f
                    )

                    orders.forEach { order ->
                        Marker(
                            state = MarkerState(position = LatLng(order.lat, order.lng)),
                            title = "Order: ${order.customerName}",
                            snippet = "${order.neighborhood} • ${order.statusEnum().label}",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                        )
                    }
                }

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
                        .testTag("toggle_store_map_gestures_button")
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
                            color = if (isMapGestureEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            if (!locationNotice.isNullOrBlank()) {
                Text(
                    text = locationNotice!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                if (loc != null) {
                                    storeLat = loc.latitude
                                    storeLng = loc.longitude
                                    locationNotice = "Pinned store to current device location."
                                } else {
                                    locationNotice = "Location unavailable; tap on map to set pin."
                                }
                            }
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pin_current_location_button")
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pin My Location")
                }

                Button(
                    onClick = {
                        viewModel.saveStoreConfig(
                            storeName = storeName,
                            address = address,
                            lat = storeLat,
                            lng = storeLng,
                            radiusKm = radiusKm
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_store_map_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Zone")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        label = { Text("Store Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Store Locality / Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column {
                        Text(
                            text = "Delivery Coverage Radius: ${radiusKm.roundToInt()} km (${orders.size} active delivery pins)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Slider(
                            value = radiusKm.toFloat(),
                            onValueChange = { radiusKm = it.toDouble() },
                            valueRange = 1f..20f
                        )
                    }
                }
            }
        }
    }
}
