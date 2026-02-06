package com.gpstracking

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.gpstracking.Room.AppDatabase
import com.gpstracking.Room.LocationPointEntity
import com.gpstracking.Services.LocationTrackingService
import com.gpstracking.databinding.ActivitySessionDetailBinding
import com.gpstracking.utils.DateTimeUtils
import com.gpstracking.utils.DistanceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivitySessionDetailBinding
    private var googleMap: GoogleMap? = null
    private var sessionId = 0L
    private var locationPoints: MutableList<LocationPointEntity> = mutableListOf()
    private var polyline: Polyline? = null
    private var isLiveSession = false
    private var startTime = 0L
    private var endTime: Long? = null
    private var currentLocationMarker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var boundsBuilder: LatLngBounds.Builder? = null

    private var statisticsBottomSheet: SessionStatisticsBottomSheet? = null

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationTrackingService.ACTION_LOCATION_UPDATE) {
                val receivedSessionId = intent.getLongExtra(LocationTrackingService.EXTRA_SESSION_ID, 0L)

                if (receivedSessionId == sessionId) {
                    val lat = intent.getDoubleExtra(LocationTrackingService.EXTRA_LAT, 0.0)
                    val lng = intent.getDoubleExtra(LocationTrackingService.EXTRA_LNG, 0.0)

                    val newPoint = LocationPointEntity(
                        sessionId = sessionId,
                        lat = lat,
                        lng = lng,
                        timestamp = System.currentTimeMillis()
                    )
                    locationPoints.add(newPoint)

                    updateMapWithNewPoint(newPoint)
                    updateBottomSheet()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionId = intent.getLongExtra("session_id", 0)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupToolbar()
        setupMap()
        setupControls()
        loadSessionData()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(LocationTrackingService.ACTION_LOCATION_UPDATE)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(locationReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(locationReceiver)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Session #$sessionId"
        }
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupControls() {
        binding.btnZoomIn.setOnClickListener {
            googleMap?.animateCamera(CameraUpdateFactory.zoomIn())
        }

        binding.btnZoomOut.setOnClickListener {
            googleMap?.animateCamera(CameraUpdateFactory.zoomOut())
        }

        binding.btnCenterRoute.setOnClickListener {
            centerOnRoute()
        }

        binding.btnShowStats.setOnClickListener {
            showStatisticsDialog()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.apply {
            uiSettings.isZoomGesturesEnabled = true
            uiSettings.isScrollGesturesEnabled = true
            uiSettings.isRotateGesturesEnabled = true
            uiSettings.isTiltGesturesEnabled = true
            uiSettings.isCompassEnabled = true

            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMyLocationButtonEnabled = true

            setMinZoomPreference(3f)
            setMaxZoomPreference(21f)

            enableMyLocation()

            mapType = GoogleMap.MAP_TYPE_NORMAL
        }

        if (locationPoints.isNotEmpty()) {
            drawRoute()
        }
    }

    private fun enableMyLocation() {
        val map = googleMap ?: return

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
        }
    }

    private fun loadSessionData() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                AppDatabase.get(this@SessionDetailActivity)
                    .sessionDao()
                    .getSessionById(sessionId)
            }

            val points = withContext(Dispatchers.IO) {
                AppDatabase.get(this@SessionDetailActivity)
                    .locationDao()
                    .getLocationsForSession(sessionId)
            }

            locationPoints = points.toMutableList()

            binding.progressBar.visibility = View.GONE

            if (session != null) {
                startTime = session.startTime
                endTime = session.endTime
                isLiveSession = endTime == null

                if (isLiveSession) {
                    binding.liveIndicatorCard.visibility = View.VISIBLE
                    startLiveIndicatorAnimation()
                } else {
                    binding.liveIndicatorCard.visibility = View.GONE
                }

                if (locationPoints.isNotEmpty()) {
                    if (googleMap != null) {
                        drawRoute()
                    }
                } else {
                    binding.tvEmptyState.visibility = View.VISIBLE
                }
            } else {
                binding.tvEmptyState.visibility = View.VISIBLE
            }
        }
    }

    private fun startLiveIndicatorAnimation() {
        val blinkAnimation = AlphaAnimation(0.0f, 1.0f).apply {
            duration = 800
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        binding.liveIndicatorDot.startAnimation(blinkAnimation)
    }

    private fun showStatisticsDialog() {
        val distance = DistanceUtils.calculateDistance(locationPoints)
        val duration = if (endTime != null) {
            DistanceUtils.calculateDuration(startTime, endTime!!)
        } else {
            DistanceUtils.calculateDuration(startTime, System.currentTimeMillis())
        }

        statisticsBottomSheet = SessionStatisticsBottomSheet.newInstance(
            distance = distance,
            duration = duration,
            pointsCount = locationPoints.size,
            startTime = startTime,
            endTime = endTime
        )

        statisticsBottomSheet?.show(supportFragmentManager, "statistics_bottom_sheet")
    }

    private fun updateBottomSheet() {
        statisticsBottomSheet?.let { sheet ->
            if (sheet.isVisible) {
                val distance = DistanceUtils.calculateDistance(locationPoints)
                val duration = DistanceUtils.calculateDuration(startTime, System.currentTimeMillis())

                sheet.updateData(
                    distance = distance,
                    duration = duration,
                    pointsCount = locationPoints.size,
                    startTime = startTime,
                    endTime = endTime
                )
            }
        }
    }

    private fun drawRoute() {
        val map = googleMap ?: return
        if (locationPoints.isEmpty()) return

        map.clear()
        boundsBuilder = LatLngBounds.Builder()

        val polylineOptions = PolylineOptions()
            .color(Color.parseColor("#2196F3"))
            .width(10f)
            .geodesic(true)

        locationPoints.forEach { point ->
            val latLng = LatLng(point.lat, point.lng)
            polylineOptions.add(latLng)
            boundsBuilder?.include(latLng)

            map.addCircle(
                CircleOptions()
                    .center(latLng)
                    .radius(0.3)
                    .fillColor(Color.parseColor("#FF0000"))
                    .strokeColor(Color.parseColor("#000000"))
                    .strokeWidth(2f)
                    .zIndex(2f)
            )
        }

        polyline = map.addPolyline(polylineOptions)

        val startPoint = locationPoints.first()
        val startLatLng = LatLng(startPoint.lat, startPoint.lng)
        map.addMarker(
            MarkerOptions()
                .position(startLatLng)
                .title("Start")
                .snippet(DateTimeUtils.formatDateTime(startPoint.timestamp))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .zIndex(2f)
        )

        val endPoint = locationPoints.last()
        val endLatLng = LatLng(endPoint.lat, endPoint.lng)
        currentLocationMarker = map.addMarker(
            MarkerOptions()
                .position(endLatLng)
                .title(if (isLiveSession) "Current Position" else "End")
                .snippet(DateTimeUtils.formatDateTime(endPoint.timestamp))
                .icon(BitmapDescriptorFactory.defaultMarker(
                    if (isLiveSession) BitmapDescriptorFactory.HUE_AZURE else BitmapDescriptorFactory.HUE_RED
                ))
                .zIndex(2f)
        )

        centerOnRoute()
    }

    private fun centerOnRoute() {
        val map = googleMap ?: return
        val builder = boundsBuilder ?: return

        try {
            val bounds = builder.build()
            val padding = 150
            val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
            map.animateCamera(cameraUpdate, 1000, null)
        } catch (e: Exception) {
            if (locationPoints.isNotEmpty()) {
                val firstPoint = locationPoints.first()
                val latLng = LatLng(firstPoint.lat, firstPoint.lng)
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }
        }
    }

    private fun updateMapWithNewPoint(newPoint: LocationPointEntity) {
        val map = googleMap ?: return

        val newLatLng = LatLng(newPoint.lat, newPoint.lng)

        boundsBuilder?.include(newLatLng)

        map.addCircle(
            CircleOptions()
                .center(newLatLng)
                .radius(0.5)
                .fillColor(Color.parseColor("#FF0000"))
                .strokeColor(Color.parseColor("#5C5858"))
                .strokeWidth(3f)
                .zIndex(1f)
        )

        polyline?.let { line ->
            val points = line.points.toMutableList()
            points.add(newLatLng)
            line.points = points
        }

        currentLocationMarker?.let { marker ->
            marker.position = newLatLng
            marker.snippet = DateTimeUtils.formatDateTime(newPoint.timestamp)
        } ?: run {
            currentLocationMarker = map.addMarker(
                MarkerOptions()
                    .position(newLatLng)
                    .title("Current Position")
                    .snippet(DateTimeUtils.formatDateTime(newPoint.timestamp))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    .zIndex(2f)
            )
        }

        map.animateCamera(CameraUpdateFactory.newLatLng(newLatLng), 500, null)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.liveIndicatorDot.clearAnimation()
        polyline = null
        currentLocationMarker = null
        googleMap = null
        boundsBuilder = null
        statisticsBottomSheet = null
    }
}