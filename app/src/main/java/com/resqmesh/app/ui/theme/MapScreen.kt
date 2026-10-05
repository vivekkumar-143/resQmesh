//package com.resqmesh.app.ui.theme
//
//import android.Manifest
//import android.content.pm.PackageManager
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.DisposableEffect
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.core.content.ContextCompat
//import androidx.compose.ui.viewinterop.AndroidView
//import com.google.android.gms.location.LocationServices
//import org.osmdroid.config.Configuration
//import org.osmdroid.tileprovider.tilesource.TileSourceFactory
//import org.osmdroid.util.GeoPoint
//import org.osmdroid.views.MapView
//import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
//import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
//
//@Composable
//fun MapScreen() {
//
//    val context = LocalContext.current
//
//    val hasLocationPermission =
//        ContextCompat.checkSelfPermission(
//            context,
//            Manifest.permission.ACCESS_FINE_LOCATION
//        ) == PackageManager.PERMISSION_GRANTED ||
//                ContextCompat.checkSelfPermission(
//                    context,
//                    Manifest.permission.ACCESS_COARSE_LOCATION
//                ) == PackageManager.PERMISSION_GRANTED
//
//    LaunchedEffect(Unit) {
//
//        Configuration.getInstance().load(
//            context,
//            context.getSharedPreferences(
//                "osmdroid",
//                0
//            )
//        )
//
//        Configuration.getInstance().userAgentValue =
//            context.packageName
//    }
//
//    AndroidView(
//        modifier = Modifier.fillMaxSize(),
//
//        factory = { ctx ->
//
//            MapView(ctx).apply {
//
//                setTileSource(
//                    TileSourceFactory.MAPNIK
//                )
//
//                setMultiTouchControls(true)
//
//                controller.setZoom(15.0)
//
//                if (hasLocationPermission) {
//
//                    val locationOverlay =
//                        MyLocationNewOverlay(
//                            GpsMyLocationProvider(ctx),
//                            this
//                        )
//
//                    locationOverlay.enableMyLocation()
//
//                    locationOverlay.enableFollowLocation()
//
//                    overlays.add(locationOverlay)
//
//                    // Initial fallback location
//                    controller.setCenter(
//                        GeoPoint(
//                            22.8046,
//                            86.2029
//                        )
//                    )
//
//                } else {
//
//                    // Jamshedpur fallback
//                    controller.setCenter(
//                        GeoPoint(
//                            22.8046,
//                            86.2029
//                        )
//                    )
//                }
//            }
//        },
//
//        update = { mapView ->
//
//            mapView.invalidate()
//        }
//    )
//}
//
//


package com.resqmesh.app.ui.theme

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen() {

    val context = LocalContext.current

    val hasLocationPermission =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) {

        Configuration.getInstance().load(
            context,
            context.getSharedPreferences(
                "osmdroid",
                0
            )
        )

        Configuration.getInstance().userAgentValue =
            context.packageName
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),

        factory = { ctx ->

            MapView(ctx).apply {

                // Map source
                setTileSource(
                    TileSourceFactory.MAPNIK
                )

                // Professional map interaction
                setMultiTouchControls(true)

                isTilesScaledToDpi = true

                // Initial zoom
                controller.setZoom(17.0)

                if (hasLocationPermission) {

                    val locationProvider =
                        GpsMyLocationProvider(ctx)

                    val locationOverlay =
                        MyLocationNewOverlay(
                            locationProvider,
                            this
                        )

                    // Show current location
                    locationOverlay.enableMyLocation()

                    // Automatically follow the user
                    locationOverlay.enableFollowLocation()

                    // Add location overlay
                    overlays.add(locationOverlay)

                } else {

                    // Fallback location
                    controller.setCenter(
                        GeoPoint(
                            22.8046,
                            86.2029
                        )
                    )
                }
            }
        },

        update = { mapView ->

            mapView.invalidate()
        }
    )
}