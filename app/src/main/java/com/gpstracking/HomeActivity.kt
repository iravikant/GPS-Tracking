package com.gpstracking

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.gpstracking.Services.LocationTrackingService
import com.gpstracking.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var isTracking = false
    private var currentSessionId = 0L
    private lateinit var prefs: SharedPreferences

    private var permissionQueue: MutableList<String> = mutableListOf()
    private var isProcessingPermissions = false

    companion object {
        private const val TAG = "HomeActivity"
        private const val PREFS_NAME = "tracking_prefs"
        private const val KEY_IS_TRACKING = "is_tracking"
        private const val KEY_SESSION_ID = "session_id"
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val REQUEST_IGNORE_BATTERY_OPTIMIZATION = 2001

    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IGNORE_BATTERY_OPTIMIZATION) {
            if (isIgnoringBatteryOptimizations()) {
                Toast.makeText(this, "Battery optimization disabled", Toast.LENGTH_SHORT).show()
                handleStartTracking()
            } else {
                Toast.makeText(
                    this,
                    "Battery optimization still enabled. Background tracking may stop.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationTrackingService.ACTION_LOCATION_UPDATE) {
                val lat = intent.getDoubleExtra(LocationTrackingService.EXTRA_LAT, 0.0)
                val lng = intent.getDoubleExtra(LocationTrackingService.EXTRA_LNG, 0.0)
                currentSessionId = intent.getLongExtra(LocationTrackingService.EXTRA_SESSION_ID, 0L)

                saveTrackingState(true, currentSessionId)

                updateTrackingUI(lat, lng)
            }
        }
    }
    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(packageName)
        }
        return true
    }

    private val settingsIntentLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (hasAllRequiredPermissions()) {
                startTracking()
            } else {
                Toast.makeText(
                    this,
                    "Required permissions are still not granted",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setupViews()
        restoreTrackingState()
    }

    private fun startLiveIndicatorAnimation() {
        val blinkAnimation = AlphaAnimation(0.0f, 1.0f).apply {
            duration = 800
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        binding.statusIndicator.startAnimation(blinkAnimation)
    }
    private fun requestBatteryOptimizationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AlertDialog.Builder(this)
                .setTitle("Disable Battery Optimization")
                .setMessage(
                    "To ensure uninterrupted GPS tracking, please allow this app to run without battery restrictions.\n\n" +
                            "This prevents Android from stopping tracking in the background."
                )
                .setPositiveButton("Allow") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATION)
                    } catch (e: Exception) {
                        Log.e(TAG, "Battery optimization intent failed", e)
                    }
                }
                .setNegativeButton("Skip") { _, _ ->
                    Toast.makeText(
                        this,
                        "Tracking may stop in background due to battery restrictions",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun setupViews() {
        binding.btnStartStop.setOnClickListener {
            if (isTracking) {
                showStopTrackingDialog()
            } else {
                handleStartTracking()
            }
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, SessionHistoryActivity::class.java))
        }

        binding.btnViewCurrent.setOnClickListener {
            if (currentSessionId > 0) {
                val intent = Intent(this, SessionDetailActivity::class.java).apply {
                    putExtra("session_id", currentSessionId)
                }
                startActivity(intent)
            }
        }
    }

    private fun restoreTrackingState() {
        isTracking = prefs.getBoolean(KEY_IS_TRACKING, false)
        currentSessionId = prefs.getLong(KEY_SESSION_ID, 0L)

        if (isTracking) {
            updateButtonState()
            binding.trackingInfoCard.visibility = View.VISIBLE
            startLiveIndicatorAnimation()
            if (!isServiceRunning()) {
                isTracking = false
                currentSessionId = 0L
                saveTrackingState(false, 0L)
                updateButtonState()
                binding.trackingInfoCard.visibility = View.GONE
            }
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (LocationTrackingService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun saveTrackingState(tracking: Boolean, sessionId: Long) {
        prefs.edit().apply {
            putBoolean(KEY_IS_TRACKING, tracking)
            putLong(KEY_SESSION_ID, sessionId)
            apply()
        }
    }


    private fun handleStartTracking() {
        if (!isIgnoringBatteryOptimizations()) {
            requestBatteryOptimizationPermission()
            return
        }

        if (hasAllRequiredPermissions()) {
            startTracking()
        } else {
            requestPermissionsSequentially()
        }
    }



    private fun hasAllRequiredPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return fineLocation && coarseLocation && backgroundLocation && notificationPermission
    }


    private fun requestPermissionsSequentially() {
        permissionQueue.clear()
        isProcessingPermissions = true

        val foregroundPermissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            foregroundPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            foregroundPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (foregroundPermissions.isNotEmpty()) {
            permissionQueue.addAll(foregroundPermissions)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionQueue.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                permissionQueue.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        processNextPermissionRequest()
    }


    private fun processNextPermissionRequest() {
        if (permissionQueue.isEmpty()) {
            isProcessingPermissions = false

            if (hasAllRequiredPermissions()) {
                startTracking()
            } else {
                Log.w(TAG, "Not all permissions granted after request sequence")
            }
            return
        }

        val permission = permissionQueue.first()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            permission == Manifest.permission.ACCESS_BACKGROUND_LOCATION) {

            permissionQueue.removeAt(0)

            showBackgroundLocationRationale {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            permission == Manifest.permission.POST_NOTIFICATIONS) {

            permissionQueue.removeAt(0)

            showNotificationPermissionRationale {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
        else {
            val permissionsToRequest = if (permission == Manifest.permission.ACCESS_BACKGROUND_LOCATION ||
                permission == Manifest.permission.POST_NOTIFICATIONS) {
                permissionQueue.removeAt(0)
                arrayOf(permission)
            } else {
                val foregroundPerms = permissionQueue.filter {
                    it != Manifest.permission.ACCESS_BACKGROUND_LOCATION &&
                            it != Manifest.permission.POST_NOTIFICATIONS
                }
                permissionQueue.removeAll(foregroundPerms)
                foregroundPerms.toTypedArray()
            }

            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest,
                PERMISSION_REQUEST_CODE
            )
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission granted: ${permissions[0]}")

                when (permissions[0]) {
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION -> {
                        Toast.makeText(
                            this,
                            "Background location access granted",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    Manifest.permission.POST_NOTIFICATIONS -> {
                        Toast.makeText(
                            this,
                            "Notification permission granted",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                processNextPermissionRequest()

            } else {
                Log.d(TAG, "Permission denied: ${permissions.getOrNull(0) ?: "unknown"}")

                when {
                    permissions.isNotEmpty() && permissions[0] == Manifest.permission.ACCESS_BACKGROUND_LOCATION -> {
                        Toast.makeText(
                            this,
                            "Background location not granted. Tracking may be limited when app is closed.",
                            Toast.LENGTH_LONG
                        ).show()
                        processNextPermissionRequest()
                    }

                    permissions.isNotEmpty() && permissions[0] == Manifest.permission.POST_NOTIFICATIONS -> {
                        Toast.makeText(
                            this,
                            "Notification permission denied. You won't see tracking notifications.",
                            Toast.LENGTH_LONG
                        ).show()
                        processNextPermissionRequest()
                    }

                    else -> {
                        showPermissionSettingsDialog(
                            title = "Location Permission Required",
                            message = "This app needs location access to track your GPS coordinates. " +
                                    "Please enable location permissions in Settings.",
                            onSettingsClick = {
                                openAppSettings()
                            },
                            onCancelClick = {
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }


    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        val allGranted = permissions.values.all { it }

        if (allGranted) {
            startTracking()
        } else {
            val deniedPermissions = permissions.filterValues { !it }.keys

            val shouldShowRationale = deniedPermissions.any { permission ->
                ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
            }

            if (shouldShowRationale) {
                showPermissionRationaleDialog()
            } else {
                showPermissionSettingsDialog(
                    title = "Permissions Required",
                    message = "Required permissions need to be enabled in Settings for GPS tracking to work properly.",
                    onSettingsClick = {
                        openAppSettings()
                    },
                    onCancelClick = null
                )
            }
        }
    }


    private fun showBackgroundLocationRationale(onProceed: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("All-Time Location Access")
            .setMessage(
                "This app needs all-time location access to track your GPS coordinates " +
                        "even when the app is closed or not in use.\n\n" +
                        "On the next screen, please select \"Allow all the time\" for location access."
            )
            .setPositiveButton("Continue") { _, _ ->
                onProceed()
            }
            .setNegativeButton("Skip") { _, _ ->
                Toast.makeText(
                    this,
                    "Background tracking will be limited",
                    Toast.LENGTH_SHORT
                ).show()
                processNextPermissionRequest()
            }
            .setCancelable(false)
            .show()
    }


    private fun showNotificationPermissionRationale(onProceed: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission")
            .setMessage(
                "This app needs notification permission to show you tracking status updates " +
                        "and keep the tracking service running in the background.\n\n" +
                        "This helps you stay informed about your active tracking sessions."
            )
            .setPositiveButton("Allow") { _, _ ->
                onProceed()
            }
            .setNegativeButton("Skip") { _, _ ->
                Toast.makeText(
                    this,
                    "You won't receive tracking notifications",
                    Toast.LENGTH_SHORT
                ).show()
                processNextPermissionRequest()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(
                "This app needs the following permissions:\n\n" +
                        "• Precise location access - To track your GPS coordinates\n" +
                        "• All-time location access - For background tracking\n" +
                        "• Notifications - To show tracking status"
            )
            .setPositiveButton("Grant") { _, _ ->
                requestPermissionsSequentially()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun showPermissionSettingsDialog(
        title: String,
        message: String,
        onSettingsClick: () -> Unit,
        onCancelClick: (() -> Unit)?
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                onSettingsClick()
            }
            .setNegativeButton("Cancel") { _, _ ->
                onCancelClick?.invoke()
            }
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        settingsIntentLauncher.launch(intent)
    }


    private fun startTracking() {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        isTracking = true
        saveTrackingState(true, currentSessionId)
        updateButtonState()
        binding.trackingInfoCard.visibility = View.VISIBLE
        startLiveIndicatorAnimation()
        Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show()
    }

    private fun stopTracking() {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(intent)

        isTracking = false
        val sessionToView = currentSessionId
        currentSessionId = 0L

        saveTrackingState(false, 0L)
        updateButtonState()
        binding.trackingInfoCard.visibility = View.GONE

        if (sessionToView > 0) {
            val detailIntent = Intent(this, SessionDetailActivity::class.java).apply {
                putExtra("session_id", sessionToView)
            }
            startActivity(detailIntent)
        }
    }

    private fun updateButtonState() {
        if (isTracking) {
            binding.btnStartStop.apply {
                text = "Stop Tracking"
                setBackgroundColor(getColor(R.color.red))
                icon = getDrawable(R.drawable.ic_stop)
            }
            binding.statusIndicator.visibility = View.VISIBLE
        } else {
            binding.btnStartStop.apply {
                text = "Start Tracking"
                setBackgroundColor(getColor(R.color.primary))
                icon = getDrawable(R.drawable.ic_play)
            }
            binding.statusIndicator.visibility = View.GONE
        }
    }

    private fun updateTrackingUI(lat: Double, lng: Double) {
        binding.tvCurrentLat.text = String.format("%.6f", lat)
        binding.tvCurrentLng.text = String.format("%.6f", lng)
    }

    private fun showStopTrackingDialog() {
        AlertDialog.Builder(this)
            .setTitle("Stop Tracking")
            .setMessage("Do you want to stop the current tracking session?")
            .setPositiveButton("Stop") { _, _ ->
                stopTracking()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        restoreTrackingState()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}