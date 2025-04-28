package net.heidylazaro.backtohome

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import com.google.accompanist.permissions.*
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.SquareCap
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setContent {
            MyApp()
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun MyApp() {
        val context = LocalContext.current
        val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
        var targetLocation by remember { mutableStateOf<LatLng?>(null) }
        var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
        var trigger by remember { mutableStateOf(0) }
        var currentLocation by remember { mutableStateOf<LatLng?>(null) }

        if (locationPermissionState.status is PermissionStatus.Granted) {
            Column(modifier = Modifier.fillMaxSize().background(color = Color(255,240,224))) {
                SearchLocation(
                    onLocationSelected = { latLng ->
                        targetLocation = latLng
                        trigger++
                    },
                    onRouteRequested = { destinationLatLng ->
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                if (location != null && googleMap != null) {
                                    val origin = LatLng(location.latitude, location.longitude)
                                    drawRoute(origin, destinationLatLng, googleMap!!)
                                } else {
                                    Toast.makeText(context, "Error con ubicación o mapa no disponible.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Permiso de ubicación no otorgado.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    currentLocation = currentLocation
                )
                MapViewComposable(targetLocation, trigger, { map -> googleMap = map }, { location ->
                    currentLocation = location
                })
            }
        } else {
            LaunchedEffect(Unit) {
                locationPermissionState.launchPermissionRequest()
            }
        }
    }

    @Composable
    fun SearchLocation(
        onLocationSelected: (LatLng) -> Unit,
        onRouteRequested: (LatLng) -> Unit,
        currentLocation: LatLng?
    ) {
        var text by remember { mutableStateOf("") }
        var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
        var selectedAddress by remember { mutableStateOf<String?>(null) }
        var selectedLatLng by remember { mutableStateOf<LatLng?>(null) }
        var hasMovedCamera by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        currentLocation?.let { location ->
                            onLocationSelected(location)
                        }
                    },
                    enabled = currentLocation != null, // Importante: que solo controle si se puede usar
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color(0xFFBDBDBD) // Gris cuando está deshabilitado
                    ),
                    modifier = Modifier
                        .width(200.dp)
                        .padding(vertical = 8.dp)
                ) {
                    Text("Centrar en mi ubicación")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = text,
                onValueChange = { newText ->
                    text = newText
                    hasMovedCamera = false // Resetear estado al escribir algo nuevo

                    if (newText.length >= 3) {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val results = try {
                            geocoder.getFromLocationName(newText, 5)
                        } catch (e: Exception) {
                            emptyList()
                        }
                        suggestions = results?.mapNotNull { it.getAddressLine(0) } ?: emptyList()
                    } else {
                        suggestions = emptyList()
                    }
                },
                label = { Text("Buscar dirección...") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                items(suggestions) { suggestion ->
                    Text(
                        text = suggestion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedAddress = suggestion
                                text = suggestion
                                suggestions = emptyList()
                            }
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly, // Espaciado entre botones
                verticalAlignment = Alignment.CenterVertically // Alinea los botones verticalmente
            ) {
                Button(
                    onClick = {
                        selectedAddress?.let { address ->
                            val geocoder = Geocoder(context, Locale.getDefault())
                            val result = try {
                                geocoder.getFromLocationName(address, 1)?.firstOrNull()
                            } catch (e: Exception) {
                                null
                            }

                            result?.let {
                                val latLng = LatLng(it.latitude, it.longitude)
                                selectedLatLng = latLng
                                onLocationSelected(latLng)
                                hasMovedCamera = true
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f) // Hace que ambos botones ocupen el mismo espacio
                        .padding(end = 8.dp), // Un poco de separación entre botones
                    enabled = selectedAddress != null
                ) {
                    Text("Buscar ubicación")
                }

                Button(
                    onClick = {
                        selectedLatLng?.let {
                            onRouteRequested(it)
                        }
                    },
                    modifier = Modifier
                        .weight(1f) // Hace que ambos botones ocupen el mismo espacio
                        .padding(start = 8.dp), // Un poco de separación entre botones
                    enabled = hasMovedCamera
                ) {
                    Text("Trazar ruta")
                }
            }
        }
    }

    private fun drawRoute(origin: LatLng, destination: LatLng, map: GoogleMap) {
        val apiKey = "AIzaSyCT5X1QSoGJQjEeT8No_wiy7GxhGl7aDzo"
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${origin.latitude},${origin.longitude}" +
                "&destination=${destination.latitude},${destination.longitude}" +
                "&mode=driving&key=$apiKey"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = URL(url).readText()
                val jsonObject = JSONObject(response)
                val routes = jsonObject.getJSONArray("routes")

                // Guarda las posiciones de los marcadores
                val currentMarker = MarkerOptions().position(origin).title("Mi ubicación actual")
                val destinationMarker = MarkerOptions().position(destination).title("Dirección seleccionada")

                withContext(Dispatchers.Main) {
                    map.clear()

                    if (routes.length() > 0) {
                        val points = routes.getJSONObject(0)
                            .getJSONObject("overview_polyline")
                            .getString("points")
                        val decodedPath = decodePolyline(points)

                        // Dibujamos la ruta
                        map.addPolyline(
                            PolylineOptions()
                                .addAll(decodedPath)
                                .width(12f) // Grosor mayor para una mejor visualización
                                .color(android.graphics.Color.MAGENTA)
                                .startCap(SquareCap())
                                .endCap(SquareCap())
                                .jointType(JointType.ROUND) // Suaviza las esquinas
                        )
                    } else {
                        Toast.makeText(this@MainActivity, "No se encontró ruta disponible.", Toast.LENGTH_SHORT).show()
                    }

                    map.addMarker(currentMarker)
                    map.addMarker(destinationMarker)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error obteniendo ruta: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val p = LatLng(lat / 1E5, lng / 1E5)
            poly.add(p)
        }

        return poly
    }

    @Composable
    fun MapViewComposable(
        targetLocation: LatLng?,
        trigger: Int,
        onMapReady: (GoogleMap) -> Unit,
        onCurrentLocationObtained: (LatLng) -> Unit
    ) {
        val context = LocalContext.current
        var mapInstance by remember { mutableStateOf<GoogleMap?>(null) }

        AndroidView(factory = { ctx ->
            val mapView = MapView(ctx)
            mapView.onCreate(null)
            mapView.getMapAsync { map ->
                mapInstance = map
                onMapReady(map)
                map.mapType = GoogleMap.MAP_TYPE_HYBRID
                map.isTrafficEnabled = true
                map.uiSettings.isZoomControlsEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = false

                if (ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // map.isMyLocationEnabled = true
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(ctx)
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            val currentLatLng = LatLng(location.latitude, location.longitude)
                            map.addMarker(MarkerOptions().position(currentLatLng).title("Mi ubicación actual"))
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                            onCurrentLocationObtained(currentLatLng)
                        }
                    }
                }
            }
            mapView
        }, modifier = Modifier.fillMaxSize())

        LaunchedEffect(trigger) {
            targetLocation?.let { location ->
                mapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
                mapInstance?.addMarker(MarkerOptions().position(location).title("Dirección seleccionada"))
            }
        }
    }
}
