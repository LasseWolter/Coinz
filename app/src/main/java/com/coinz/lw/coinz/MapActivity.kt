package com.coinz.lw.coinz

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.location.Location
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Exception

enum class Currency {
    SHIL, DOLR, QUID, PENY
}

class Coin(val id: String, val value: Double, val currency: Currency, val location: LatLng)

// The mapActivity is passed as parameter such that the UI can be updated on coin collection
class Wallet(val mapActivity: Activity){
    private val baseTag = "WALLET"
    private var rates = hashMapOf<Currency, Double>()

    private var coins = mutableListOf<Coin>()
    private var goldVal: Double = 0.0

    override fun toString(): String {
        var result = ""
        coins.forEachIndexed() { i, coin ->
            result += "\n${i + 1}: ${coin.value} ${coin.currency} found at ${coin.location}"
        }
        return result
    }

    // Update currency rates to most recent ones available
    fun updateRates(jsonStr: String) {
        val tag = "$baseTag [updateRates]"
        val parser = JsonParser()
        val obj = parser.parse(jsonStr).asJsonObject
        var curRates = obj.get("rates").asJsonObject

        enumValues<Currency>().forEach {
            this.rates[it] = curRates.get(it.name).asDouble
        }

    }

    // Convert currency value to Gold value
    private fun convert(value: Double, currency: Currency): Double {
        val tag = "$baseTag [updateRates]"
        var goldValue = 0.0

        // If there is no rate then we don't want to add any gold value so we goldValue stays 0.0
        if (rates[currency] == null) {
            Log.d(tag, "No rate available for currency $currency. Is it a valid currency? value of 0.0 returned")
        } else {
            goldValue = value * rates[currency]!!    // by this point we know that rates[currency] cannot be null
        }
        return goldValue
    }

    // Only add coin to wallet when no coin with that id is already in the wallet
    fun addCoin(coin: Coin) {
        val tag = "$baseTag [addCoin]"

        var alreadyCollected = false
        for (colCoin in coins) {
            if (coin.id == colCoin.id) {
                Log.d(tag, "This Coin has already been collected. id: ${coin.id}")
                alreadyCollected = true
                break
            }
        }
        if (!alreadyCollected) {
            coins.add(coin)
            val coinVal = convert(coin.value, coin.currency)
            mapActivity.alert("Congratulations you just found a coin worth %.2f".format(coinVal)) {
                isCancelable = false
                positiveButton("Continue") {
                    goldVal += coinVal
                    Log.d(tag, "GOLD: $goldVal")
                    mapActivity.gold_counter.text = "%.2f".format(goldVal)
                }
            }.show()

        }
    }

}

class MapActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener,
        LocationEngineListener {

    private val baseTag = "MAP_ACTIVITY"
    private lateinit var mapView: MapView
    private lateinit var map: MapboxMap

    private lateinit var permissionManager: PermissionsManager
    private lateinit var originLocation: Location

    private var locationEngine: LocationEngine? = null

    private var coins = mutableListOf<Coin>()
    private var wallet = Wallet(this)

    private val collectionRadius = 25


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
        mapView = findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

    }

    override fun onMapReady(mapboxMap: MapboxMap?) {
        val tag = "$baseTag [onMapReady]"


        if (mapboxMap == null) {
            Log.d(tag, "the returned map is null")
        } else {
            map = mapboxMap
            // Set user interface options
            map.uiSettings.isZoomControlsEnabled = true

            // Make location information available
            enableLocation()
        }
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
        val df = SimpleDateFormat("yyyy/MM/dd")  // Choose format that matches the URL
        val date = df.format(Calendar.getInstance().time)
        val urlStr = "$baseUrl$date/coinzmap.geojson"

        doAsync {
            Log.d(tag, "Trying to download map for date: $date")
            try {
                var result = URL(urlStr).readText()
                uiThread {
                    Log.d(tag, "Successfully downloaded daily map")
                    addMarkersFromGeoJson(result)
                    wallet.updateRates(result)
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
    private fun addCoinToList(props: JsonObject, pos: LatLng) {
        val tag = "$baseTag [addCoinToList]"
        try {
            val id = props.get("id").asString
            val value = props.get("value").asDouble
            val curStr = props.get("currency").asString
            val currency = Currency.valueOf(curStr)
            val coin = Coin(id, value, currency, pos)
            coins.add(coin)
            Log.d(tag, "Added Coin instance to list: [id: $id, " +
                    "value: $value, currency: $curStr, location: $pos]")
        } catch (e: Exception) {
            Log.d(tag, "Problem when parsing Json: $e. Coin Instance not created")
        }
    }

    // Add markers to the map and add each coin to list of coins
    private fun addMarkersFromGeoJson(jsonStr: String) {
        val tag = "$baseTag [addMarkersFromGeoJson]"
        val featureCollection = FeatureCollection.fromJson(jsonStr)
        val features = featureCollection.features()

        features?.let { feats ->
            for (feature in feats) {
                if (feature.geometry() is Point) {  // We are only interested in points
                    // Extract Point location and display marker on map
                    val point = feature.geometry() as Point
                    val coordinates = point.coordinates()
                    val pos = LatLng(coordinates[1], coordinates[0])
                    map.addMarker(MarkerOptions().position(pos))
                    Log.d(tag, "Added marker at $pos")

                    // Create Coin instance from Point properties and add it to coins list
                    val props = feature.properties()
                    if (props == null) {
                        Log.d(tag, "properties are null - no Coin instance created for location: " + pos.toString())
                    } else {
                        addCoinToList(props, pos)
                    }

                }
            }
        }
    }

    private fun setCameraPosition(location: Location) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude), 16.0))
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        val tag = "$baseTag [onExplanationNeeded]"
        Log.d(tag, "Permissions: $permissionsToExplain")
        // Present toast or dialog to explain why the need to grant permission
    }

    override fun onPermissionResult(granted: Boolean) {
        val tag = "$baseTag [onPermissionResult]"
        Log.d(tag, "granted == $granted")
        if (granted) {
            enableLocation()
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
        val userLoc = LatLng(location.latitude, location.longitude)
        for (coin in coins) {
            if (coin.location.distanceTo(userLoc) <= collectionRadius) {
                wallet.addCoin(coin)
                Log.d(tag, wallet.toString())
            }
        }
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
        super.onStop()
        locationEngine?.removeLocationUpdates()
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
