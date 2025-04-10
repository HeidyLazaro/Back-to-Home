package net.heidylazaro.backtohome

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.setContent

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private val LOCATION_PERMISSION_REQUEST_CODE = 1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crear contenedor para el mapa
        val frameLayout = FrameLayout(this).apply {
            id = FrameLayout.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Establecer el contenedor como la vista principal
        setContentView(frameLayout)

        // Crear el fragmento del mapa y agregarlo al contenedor
        val mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .add(frameLayout.id, mapFragment)
            .commit()

        // Registrar callback
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
       /* val location = LatLng(0.0, 0.0)
        googleMap.addMarker(MarkerOptions().position(location).title("Marker"))
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 5f))*/

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Activar capa de ubicación si se tiene permiso
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)

                    // Establecer tipo de mapa
                    googleMap.mapType = GoogleMap.MAP_TYPE_HYBRID

                    // Agregar marcador en la ubicación actual
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(currentLatLng)
                            .title("Ubicación actual")
                    )

                    // Mover cámara a la ubicación actual
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))

                    // Mostrar tráfico
                    googleMap.isTrafficEnabled = true
                } else {
                    Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Si no hay permiso, solicitation (esto debería ir fuera de onMapReady, en tu actividad o fragmento)
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }
}
