package com.coinz.lw.coinz

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.os.PersistableBundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.coinz.lw.coinz.Constants.Companion.USER
import com.coinz.lw.coinz.Constants.Companion.getBankGoldVal
import com.coinz.lw.coinz.Constants.Companion.getTodaysDate
import com.coinz.lw.coinz.Constants.Companion.getWalletGoldVal
import com.coinz.lw.coinz.Constants.Companion.updateDb
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonObject
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import kotlinx.android.synthetic.main.activity_map.*
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.net.URL


enum class Currency {
    SHIL, DOLR, QUID, PENY
}

class Coin(val id: String, val value: Double, val currency: Currency, val location: LatLng, val marker: Marker, var feature: Feature? = null)

class MapActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener,
        LocationEngineListener {

    private val baseTag = "MAP_ACTIVITY"
    private lateinit var mapView: MapView
    lateinit var map: MapboxMap

    private lateinit var permissionManager: PermissionsManager
    private lateinit var originLocation: Location

    private var locationEngine: LocationEngine? = null

    private var coins = mutableListOf<Coin>()

    private val collectionRadius = 25

    private var features = mutableListOf<Feature>()


    override fun onCreate(savedInstanceState: Bundle?) {
        val tag = "$baseTag [onCreate]"
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
        mapView = findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        gold_counter.text = String.format("Wallet: %d", getWalletGoldVal())
        bank_gold.text = String.format("Bank: %d", getBankGoldVal())

        logout_button.onClick { logout() }
        payIn_button.onClick {
            val walletIntent = Intent(this@MapActivity, WalletActivity::class.java)
            startActivity(walletIntent)
            Log.d(tag, "Switched to Map- to WalletActivity")
        }
    }

    override fun onMapReady(mapboxMap: MapboxMap?) {
        val tag = "$baseTag [onMapReady]"

        if (mapboxMap == null) {
            Log.d(tag, "the returned map is null")
        } else {
            map = mapboxMap

            // Make location information available
            enableLocation()
        }
    }

    private fun logout() {
        val tag = "$baseTag [logout]"
        // Access Firebase singleton object to logout
        FirebaseAuth.getInstance().signOut()

        // Switch back to Login Activity and confirm logout to user
        val logoutIntent = Intent(this@MapActivity, LoginActivity::class.java)
        startActivity(logoutIntent)
        longToast("Successfully logged out")
        Log.d(tag, "User logged out. Switch back to LoginActivity.")
    }

    private fun enableLocation() {
        val tag = "$baseTag [enableLocation]"

        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            Log.d(tag, "Permission to Location are granted")
            initializeLocationComponent()
            initializeLocationEngine()
            downloadJson()
        } else {
            Log.d(tag, "Permission to Location are not granted")
            permissionManager = PermissionsManager(this)
            permissionManager.requestLocationPermissions(this)
        }
    }

    @SuppressWarnings("MissingPermission")  // Permission is already checked in enableLocation()
    private fun initializeLocationEngine() {
        val tag = "$baseTag [initializeLocationEngine]"

        Log.d(tag, "Initialising locationEngine")
        locationEngine = LocationEngineProvider(this).obtainBestLocationEngineAvailable()
        locationEngine?.apply {
            priority = LocationEnginePriority.HIGH_ACCURACY
            interval = 5000 // check preferable every 5 seconds
            fastestInterval = 1000 // at most every second
            activate()
        }

        locationEngine?.addLocationEngineListener(this)
        locationEngine?.requestLocationUpdates()

        val lastLocation = locationEngine?.lastLocation

        if (lastLocation != null) {
            originLocation = lastLocation
            setCameraPosition(lastLocation)
        }

    }

    @SuppressWarnings("MissingPermission")  // Permission is already checked in enableLocation()
    private fun initializeLocationComponent() {
        val tag = "$baseTag [initializeLocationComponent]"

        Log.d(tag, "[initializeLocationComponent] Initialising Location Component")
        val locationComponent = map.locationComponent

        locationComponent.activateLocationComponent(this)
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.TRACKING
        locationComponent.renderMode = RenderMode.NORMAL
    }


    @SuppressLint("SimpleDateFormat")
    private fun downloadJson() {
        val tag = "$baseTag [downloadJson]"

        val baseUrl = "http://homepages.inf.ed.ac.uk/stg/coinz/"
        val urlStr = "$baseUrl${Constants.getTodaysDate()}/coinzmap.geojson"

        doAsync {
            Log.d(tag, "Trying to download map for date: ${getTodaysDate()}")
            try {
                val result = URL(urlStr).readText()
                uiThread {
                    Log.d(tag, "Successfully downloaded daily map")
                    addMarkers(result)
                    WalletControl.updateRates(result)
                }
            } catch (e: Exception) {
                Log.d(tag, "There was a problem downloading the map: $e")
                uiThread {
                    alert("The most recent app couldn't be downloaded. Please check your internet connection and try again.") {
                        positiveButton("Try again") { downloadJson() }
                        noButton {}
                    }.show()
                }
            }
        }
    }

    // Get Coin properties + create and add a coin instance to the coins list
    private fun addCoinToList(props: JsonObject, pos: LatLng, marker: Marker, feature: Feature) {
        val tag = "$baseTag [addCoinToList]"
        try {
            val id = props.get("id").asString
            val value = props.get("value").asDouble
            val curStr = props.get("currency").asString
            val currency = Currency.valueOf(curStr)
            val coin = Coin(id, value, currency, pos, marker, feature)
            coins.add(coin)
            Log.d(tag, "Added Coin instance to list: [id: $id, " +
                    "value: $value, currency: $curStr, location: $pos]")
        } catch (e: Exception) {
            Log.d(tag, "Problem when parsing Json: $e. Coin Instance not created")
        }
    }

    // Add markers to the map and add each coin to list of coins
    private fun addMarkers(baseJson: String) {
        val tag = "$baseTag [addMarkers]"
        // There is no need to fetch the user data since we have already done that during the SignIn
        // If the user has already played to day - we load the existing map

        val backedUpFeatureCol = FeatureCollection.fromJson(USER.mapJson)
        val backedUpFeatures = backedUpFeatureCol?.features()

        if (backedUpFeatures != null && backedUpFeatures.size > 0 && USER.mapJson != "" && USER.lastLogin == getTodaysDate()) {
            Log.d(tag, "Taking the already stored map")
            addMarkersFromJson(USER.mapJson)
        } else {
            addMarkersFromJson(baseJson)
        }
    }

    private fun addMarkersFromJson(jsonStr: String) {
        val tag = "$baseTag [addMarkersFromJson]"

        val featureCollection = FeatureCollection.fromJson(jsonStr)
        features = featureCollection.features() ?: features

        for (feature in features) {
            if (feature.geometry() is Point) {  // We are only interested in points
                // Extract Point location and display marker on map
                val point = feature.geometry() as Point
                val coordinates = point.coordinates()
                val pos = LatLng(coordinates[1], coordinates[0])

                val marker = map.addMarker(MarkerOptions().position(pos))
                Log.d(tag, "Added marker at $pos")

                // Create Coin instance from Point properties and add it to coins list
                val props = feature.properties()
                if (props == null) {
                    Log.d(tag, "properties are null - no Coin instance created for location: " + pos.toString())
                } else {
                    addCoinToList(props, pos, marker, feature)
                }

            }
        }
    }

    private fun setCameraPosition(location: Location) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude), 16.0))
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        // It's not really needed to display something to the user since the alert asking for the
        // permission itself is quite expressive
    }

    override fun onPermissionResult(granted: Boolean) {
        val tag = "$baseTag [onPermissionResult]"
        Log.d(tag, "granted == $granted")
        if (granted) {
            enableLocation()
        } else {
            alert(getString(R.string.alert_grant_permission)) {
                isCancelable = false
                positiveButton("Grant permission") { enableLocation() }
                negativeButton("Close App") { finish() }
            }.show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onLocationChanged(location: Location?) {
        location?.let {
            originLocation = location
            setCameraPosition(location)
            checkCoinCollected(location)
        }
    }

    // Checks if any coin is within 25 metre range and if so adds it to the collected coins
    private fun checkCoinCollected(location: Location) {
        val tag = "$baseTag [checkCoinCollected]"

        Log.d(tag, "Checking if coin collected")
        coins.removeAll { coin -> canBeCollected(coin, location) }
    }

    // Checks if a coin is close enough to be collected, if so, the coin is added to wallet and its marker is removed
    private fun canBeCollected(coin: Coin, location: Location): Boolean {
        val tag = "$baseTag [isCollected]"

        val userLoc = LatLng(location.latitude, location.longitude)
        if (coin.location.distanceTo(userLoc) <= collectionRadius) {
            map.removeMarker(coin.marker)
            WalletControl.addCoinToWallet(coin, this)
            features.remove(coin.feature)
            Log.d(tag, "Collected coin $coin and removed corresponding marker.")
            return true
        }
        return false
    }

    @SuppressWarnings("MissingPermission")  // Permission is already checked in enableLocation()
    override fun onConnected() {
        // Not needed
    }

    @SuppressWarnings("MissingPermission")  // Permission is already checked in enableLocation()
    override fun onStart() {
        super.onStart()
        mapView.onStart()
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            locationEngine?.requestLocationUpdates()
        }
    }

    @SuppressWarnings("MissingPermission")  // Permission is already checked in enableLocation()
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        gold_counter.text = String.format("Wallet: %d", getWalletGoldVal())
        bank_gold.text = String.format("Bank: %d", getBankGoldVal())
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            locationEngine?.requestLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    @SuppressWarnings("MissingPermission")  // Permission is already checked in enableLocation()
    override fun onStop() {
        val tag = "$baseTag [onStop]"
        super.onStop()
        locationEngine?.removeLocationUpdates()
        if (features.size > 0) { // to prevent storing an empty map (in case the app is closed directly after start)
            USER.mapJson = FeatureCollection.fromFeatures(features).toJson()
        }
        USER.lastLogin = getTodaysDate()
        // Update the DB with USER and COIN data
        updateDb()
        mapView.onStop()
    }

    @SuppressWarnings("MissingPermission")  // Permission is already checked in enableLocation()
    override fun onDestroy() {
        super.onDestroy()
        locationEngine?.deactivate()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?) {
        super.onSaveInstanceState(outState, outPersistentState)
        if (outState != null) {
            mapView.onSaveInstanceState(outState)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

}
