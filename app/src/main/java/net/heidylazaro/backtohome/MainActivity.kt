package net.heidylazaro.backtohome

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer

class MainActivity : ComponentActivity(), OnMapReadyCallback {
    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
    private lateinit var mapFragment: SupportMapFragment
    private var googleMap: GoogleMap? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            setupMap()
        } else {
            Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crear y configurar el FusedLocationClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Verificar permisos antes de cargar el mapa
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            setupMap()
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        setContent {
            // Definir la interfaz de usuario con Jetpack Compose
            MyApp()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        setupMap()
    }

    private fun setupMap() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = true
            googleMap?.mapType = GoogleMap.MAP_TYPE_HYBRID
            googleMap?.isTrafficEnabled = true

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    googleMap?.addMarker(
                        MarkerOptions().position(currentLatLng).title("Ubicación actual")
                    )
                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                } else {
                    Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Permiso de ubicación no otorgado", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun MyApp() {
        var targetLocation by remember { mutableStateOf<LatLng?>(null) }
        var trigger by remember { mutableStateOf(0) } // contador para forzar cambio

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            SearchLocation { latLng ->
                targetLocation = latLng
                trigger++ // incrementamos para forzar la relectura
            }
            MapViewComposable(targetLocation, trigger)
        }
    }

    @Composable
    fun SearchLocation(onLocationSelected: (LatLng) -> Unit) {
        var text by remember { mutableStateOf("") }
        val context = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Ingresa latitud,longitud") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    try {
                        val parts = text.split(",")
                        val lat = parts[0].toDouble()
                        val lng = parts[1].toDouble()
                        onLocationSelected(LatLng(lat, lng))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Formato inválido. Usa latitud,longitud", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Mover cámara")
            }
        }
    }

    @Composable
    fun MapViewComposable(targetLocation: LatLng?, trigger: Int) {
        val context = LocalContext.current
        var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

        AndroidView(factory = { ctx ->
            Log.d("MapViewComposable", "Creando MapView dentro de Compose.")

            val mapView = MapView(ctx)
            mapView.onCreate(null)

            mapView.getMapAsync { map ->
                Log.d("MapViewComposable", "Mapa listo en getMapAsync.")

                googleMap = map

                map.mapType = GoogleMap.MAP_TYPE_HYBRID
                map.isTrafficEnabled = true
                map.uiSettings.isZoomControlsEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = true

                if (ContextCompat.checkSelfPermission(
                        ctx,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    map.isMyLocationEnabled = true

                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(ctx)

                    // Agregar el marcador de ubicación actual una sola vez
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            val currentLatLng = LatLng(location.latitude, location.longitude)
                            Log.d("MapViewComposable", "Ubicación inicial: $currentLatLng")

                            map.addMarker(
                                MarkerOptions()
                                    .position(currentLatLng)
                                    .title("Mi ubicación actual")
                            )
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                        } else {
                            Log.e("MapViewComposable", "No se pudo obtener ubicación inicial.")
                        }
                    }

                    map.setOnMyLocationButtonClickListener {
                        Log.d("MapViewComposable", "Botón 'Mi ubicación' presionado.")
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            if (location != null) {
                                val currentLatLng = LatLng(location.latitude, location.longitude)
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                            }
                        }
                        true
                    }
                } else {
                    Log.e("MapViewComposable", "Permiso de ubicación no otorgado.")
                }
            }

            mapView
        },
            modifier = Modifier.fillMaxSize()
        )

        // Cuando cambie targetLocation, mover la cámara
        LaunchedEffect(trigger) {
            targetLocation?.let {
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 15f))
                Log.d("MapViewComposable", "Moviendo cámara a ubicación manual: $it")
            }
        }
    }
}

