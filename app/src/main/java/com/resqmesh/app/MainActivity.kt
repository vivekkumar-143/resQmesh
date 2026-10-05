





package com.resqmesh.app

// mport androidx.compose.foundation.layout.weight

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Build
import com.resqmesh.app.network.SharedLocation
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.resqmesh.app.database.ResQMeshDatabase
import com.resqmesh.app.model.EmergencyPacketEntity
import com.resqmesh.app.model.IncidentEntity
import com.resqmesh.app.network.NearbyMeshManager
import com.resqmesh.app.ui.EmergencyScreen
import com.resqmesh.app.ui.IncomingEmergencyScreen
import com.resqmesh.app.ui.theme.MapScreen
import com.resqmesh.app.ui.ReportIncidentScreen
import com.resqmesh.app.ui.theme.ResQMeshTheme

import androidx.compose.material3.HorizontalDivider


// =============================================================
// MAIN ACTIVITY
// =============================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContent {

            ResQMeshTheme {

                ResQMeshApp()
            }
        }
    }
}


// =============================================================
// APP
// =============================================================

@Composable
fun ResQMeshApp() {

    val context =
        LocalContext.current

    val nearbyMeshManager =
        remember {
            NearbyMeshManager(context)
        }

    var nearbyDeviceCount by remember {
        mutableStateOf(0)
    }

    var meshActive by remember {
        mutableStateOf(false)
    }

    var incomingEmergencyPacket by remember {
        mutableStateOf<EmergencyPacketEntity?>(null)
    }
    var sharedLocation by remember {
        mutableStateOf<SharedLocation?>(null)
    }

    var showLocationShareScreen by remember {
        mutableStateOf(false)
    }

    var showReportScreen by remember {
        mutableStateOf(false)
    }

    var showEmergencyScreen by remember {
        mutableStateOf(false)
    }

    var showJoinScreen by remember {
        mutableStateOf(false)
    }

    var showMapScreen by remember { mutableStateOf(false) }

    var showMessagingScreen by remember {
        mutableStateOf(false)
    }

    var showOfflineReportsScreen by remember {
        mutableStateOf(false)
    }

    var showLocalStorageScreen by remember {
        mutableStateOf(false)
    }

    var pairedDevices by remember {
        mutableStateOf<List<BluetoothDevice>>(emptyList())
    }

    /*
     * =========================================================
     * MESH MESSAGE STATE
     * =========================================================
     */

    val receivedMessages =
        remember {
            mutableStateListOf<String>()
        }


    // =========================================================
    // CALLBACKS
    // =========================================================

    LaunchedEffect(Unit) {

        nearbyMeshManager.onDeviceCountChanged =
            { count ->

                nearbyDeviceCount =
                    count

                Log.d(
                    "ResQMeshBluetooth",
                    "CONNECTED RESQMESH NODES = $count"
                )
            }


        nearbyMeshManager.onEmergencyPacketReceived =
            { packet ->

                Log.d(
                    "ResQMeshBluetooth",
                    "INCOMING SOS = ${packet.packetId}"
                )

                incomingEmergencyPacket =
                    packet
            }


        nearbyMeshManager.onLocationReceived =
            { location ->

                Log.d(
                    "ResQMeshBluetooth",
                    "INCOMING SHARED LOCATION = ${location.latitude}, ${location.longitude}"
                )

                sharedLocation = location
                showLocationShareScreen = true
            }


        /*
         * Normal mesh message callback.
         */
        nearbyMeshManager.onMessageReceived =
            { message ->

                Log.d(
                    "ResQMeshBluetooth",
                    "INCOMING MESH MESSAGE = $message"
                )

                receivedMessages.add(
                    message
                )
            }
    }


    // =========================================================
    // PERMISSIONS
    // =========================================================

    val nearbyPermissions =
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

        } else if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

        } else {

            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }


    // =========================================================
    // CREATE MESH PERMISSION
    // =========================================================

    val createMeshLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val allGranted =
                nearbyPermissions.all { permission ->

                    permissions[permission] == true ||
                            ContextCompat.checkSelfPermission(
                                context,
                                permission
                            ) ==
                            PackageManager.PERMISSION_GRANTED
                }


            if (allGranted) {

                Log.d(
                    "ResQMeshBluetooth",
                    "ALL BLUETOOTH PERMISSIONS GRANTED"
                )

                meshActive =
                    true

                nearbyMeshManager.startMesh()

            } else {

                Log.e(
                    "ResQMeshBluetooth",
                    "BLUETOOTH PERMISSIONS DENIED"
                )
            }
        }


    // =========================================================
    // JOIN MESH PERMISSION
    // =========================================================

    val joinMeshLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val allGranted =
                nearbyPermissions.all { permission ->

                    permissions[permission] == true ||
                            ContextCompat.checkSelfPermission(
                                context,
                                permission
                            ) ==
                            PackageManager.PERMISSION_GRANTED
                }


            if (allGranted) {

                Log.d(
                    "ResQMeshBluetooth",
                    "ALL BLUETOOTH PERMISSIONS GRANTED"
                )

                pairedDevices =
                    nearbyMeshManager.getPairedDevices()

                Log.d(
                    "ResQMeshBluetooth",
                    "PAIRED DEVICES = ${pairedDevices.size}"
                )

                showJoinScreen =
                    true

            } else {

                Log.e(
                    "ResQMeshBluetooth",
                    "BLUETOOTH PERMISSIONS DENIED"
                )
            }
        }


    // =========================================================
    // LOCATION SHARING
    // =========================================================

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun shareCurrentLocation() {
        if (!hasLocationPermission()) {
            Log.e("ResQMeshLocation", "LOCATION PERMISSION NOT GRANTED")
            return
        }

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { location: Location? ->
            if (location == null) {
                Log.e("ResQMeshLocation", "CURRENT LOCATION UNAVAILABLE")
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                    if (lastLocation != null) {
                        nearbyMeshManager.sendLocationShare(
                            lastLocation.latitude,
                            lastLocation.longitude
                        )
                    }
                }
                return@addOnSuccessListener
            }

            Log.d(
                "ResQMeshLocation",
                "SHARING CURRENT LOCATION = ${location.latitude}, ${location.longitude}"
            )

            nearbyMeshManager.sendLocationShare(
                location.latitude,
                location.longitude
            )
        }.addOnFailureListener { error ->
            Log.e("ResQMeshLocation", "LOCATION FETCH FAILED", error)
        }
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                        hasLocationPermission()

            if (granted) {
                shareCurrentLocation()
            } else {
                Log.e("ResQMeshLocation", "LOCATION PERMISSION DENIED")
            }
        }

    fun requestAndShareLocation() {
        if (hasLocationPermission()) {
            shareCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // =========================================================
    // MAIN ROUTING
    // =========================================================

    Surface(
        modifier =
            Modifier.fillMaxSize(),

        color =
            Color(0xFFF7F9FC)
    ) {

        when {

            // -------------------------------------------------
            // INCOMING SOS
            // -------------------------------------------------

            incomingEmergencyPacket != null -> {

                IncomingEmergencyScreen(

                    packet =
                        incomingEmergencyPacket!!,

                    onAcknowledge = {

                        incomingEmergencyPacket =
                            null
                    }
                )
            }


            // -------------------------------------------------
            // INCOMING SHARED LOCATION
            // -------------------------------------------------

            showLocationShareScreen && sharedLocation != null -> {
                LocationShareScreen(
                    location = sharedLocation!!,
                    onRoute = {
                        val target = sharedLocation!!

                        if (!hasLocationPermission()) {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else {
                            fusedLocationClient.getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                CancellationTokenSource().token
                            ).addOnSuccessListener { current ->
                                if (current != null) {
                                    val uri = Uri.parse(
                                        "https://www.google.com/maps/dir/?api=1" +
                                                "&origin=${current.latitude},${current.longitude}" +
                                                "&destination=${target.latitude},${target.longitude}" +
                                                "&travelmode=driving"
                                    )
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            }
                        }
                    },
                    onDismiss = {
                        showLocationShareScreen = false
                        sharedLocation = null
                    }
                )
            }

            // -------------------------------------------------
            // EMERGENCY
            // -------------------------------------------------

            showEmergencyScreen -> {

                EmergencyScreen(

                    onCancel = {

                        showEmergencyScreen =
                            false
                    }
                )
            }


            // -------------------------------------------------
            // REPORT
            // -------------------------------------------------

            showReportScreen -> {

                ReportIncidentScreen()
            }

            showMapScreen -> {
                MapScreen()
            }


            // -------------------------------------------------
            // JOIN
            // -------------------------------------------------

            showJoinScreen -> {

                JoinMeshScreen(

                    devices =
                        pairedDevices,

                    onBack = {

                        showJoinScreen =
                            false
                    },

                    onJoin = { device ->

                        Log.d(
                            "ResQMeshBluetooth",
                            "JOINING SELECTED DEVICE"
                        )

                        meshActive =
                            true

                        showJoinScreen =
                            false

                        nearbyMeshManager.joinMesh(
                            device
                        )
                    }
                )
            }


            // -------------------------------------------------
            // MESSAGING
            // -------------------------------------------------

            showMessagingScreen -> {

                MeshMessagingScreen(

                    connectedNodes =
                        nearbyDeviceCount,

                    messages =
                        receivedMessages,

                    onSendMessage = { message ->

                        nearbyMeshManager
                            .sendTextMessage(
                                message
                            )
                    },

                    onBack = {

                        showMessagingScreen =
                            false
                    }
                )
            }


            // -------------------------------------------------
            // OFFLINE REPORTS
            // -------------------------------------------------

            showOfflineReportsScreen -> {
                OfflineReportsScreen(
                    onBack = {
                        showOfflineReportsScreen = false
                    }
                )
            }

            // -------------------------------------------------
            // LOCAL STORAGE
            // -------------------------------------------------

            showLocalStorageScreen -> {
                LocalStorageScreen(
                    onBack = {
                        showLocalStorageScreen = false
                    }
                )
            }

            // -------------------------------------------------
            // HOME
            // -------------------------------------------------

            else -> {

                HomeScreen(

                    onCreateMesh = {
                        Log.d(
                            "ResQMeshBluetooth",
                            "REQUESTING HOST PERMISSIONS"
                        )

                        createMeshLauncher.launch(
                            nearbyPermissions
                        )
                    },

                    onJoinMesh = {
                        Log.d(
                            "ResQMeshBluetooth",
                            "REQUESTING JOIN PERMISSIONS"
                        )

                        joinMeshLauncher.launch(
                            nearbyPermissions
                        )
                    },

                    onReportIncident = {
                        showReportScreen = true
                    },

                    onEmergency = {
                        showEmergencyScreen = true
                    },

                    onMap = {
                        showMapScreen = true
                    },

                    onMessaging = {
                        showMessagingScreen = true
                    },

                    onShareLocation = {
                        requestAndShareLocation()
                    },

                    onOfflineReports = {
                        showOfflineReportsScreen = true
                    },

                    onLocalStorage = {
                        showLocalStorageScreen = true
                    },

                    nearbyDeviceCount =
                        nearbyDeviceCount,

                    meshActive =
                        meshActive
                )


            }
        }
    }
}


// =============================================================
// HOME SCREEN
// =============================================================

@Composable
fun HomeScreen(
    onMap: () -> Unit,
    onCreateMesh: () -> Unit,
    onJoinMesh: () -> Unit,
    onReportIncident: () -> Unit,
    onEmergency: () -> Unit,
    onMessaging: () -> Unit,
    onShareLocation: () -> Unit,
    onOfflineReports: () -> Unit,
    onLocalStorage: () -> Unit,
    nearbyDeviceCount: Int,
    meshActive: Boolean
) {

    val context = LocalContext.current

    var incidentCount by remember {
        mutableStateOf(0)
    }

    LaunchedEffect(Unit) {

        val database =
            ResQMeshDatabase.getDatabase(context)

        incidentCount =
            database
                .incidentDao()
                .getIncidentCount()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FC))
            .padding(
                horizontal = 18.dp,
                vertical = 14.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // =====================================================
        // HEADER
        // =====================================================

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "ResQMesh",
                    color = Color(0xFF0D47A1),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = "Emergency Response Network",
                    color = Color(0xFF667085),
                    fontSize = 12.sp
                )
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {

                Text(
                    text = if (meshActive) "● ONLINE" else "● STANDBY",
                    color = if (meshActive)
                        Color(0xFF16A34A)
                    else
                        Color(0xFF667085),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "$nearbyDeviceCount NODE" +
                            if (nearbyDeviceCount == 1) "" else "S",
                    color = Color(0xFF667085),
                    fontSize = 10.sp
                )
            }
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        // =====================================================
        // NETWORK STATUS CARD
        // =====================================================

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color.White,
                    RoundedCornerShape(16.dp)
                )
                .border(
                    1.dp,
                    Color(0xFFD9E2EC),
                    RoundedCornerShape(16.dp)
                )
                .padding(
                    horizontal = 15.dp,
                    vertical = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "NETWORK STATUS",
                    color = Color(0xFF667085),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(2.dp)
                )

                Text(
                    text = if (meshActive)
                        "Mesh network active"
                    else
                        "Ready to establish network",
                    color = Color(0xFF172033),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "$nearbyDeviceCount\nNODES",
                color = Color(0xFF1565C0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        // =====================================================
        // MAIN ACTION GRID
        // =====================================================

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            DashboardAction(
                modifier = Modifier.weight(1f),
                icon = "📡",
                title = "CREATE MESH",
                subtitle = "Start network",
                onClick = onCreateMesh
            )

            DashboardAction(
                modifier = Modifier.weight(1f),
                icon = "🔗",
                title = "JOIN MESH",
                subtitle = "Connect node",
                onClick = onJoinMesh
            )
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            DashboardAction(
                modifier = Modifier.weight(1f),
                icon = "💬",
                title = "MESSAGING",
                subtitle = "Mesh messages",
                onClick = onMessaging
            )

            DashboardAction(
                modifier = Modifier.weight(1f),
                icon = "🗺️",
                title = "MAP",
                subtitle = "Live location",
                onClick = onMap
            )
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Button(
            onClick = onShareLocation,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFEAF2FF),
                contentColor = Color(0xFF0D47A1)
            )
        ) {
            Text(
                text = "📍  SHARE MY LOCATION",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        // =====================================================
        // SOS
        // =====================================================

        Button(
            onClick = onEmergency,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD32F2F),
                contentColor = Color.White
            )
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "🚨  I'M IN DANGER",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = "Send emergency alert",
                    fontSize = 10.sp
                )
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        // =====================================================
        // REPORT INCIDENT
        // =====================================================

        Button(
            onClick = onReportIncident,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF172033)
            )
        ) {

            Text(
                text = "🚧  REPORT INCIDENT",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        // =====================================================
        // QUICK STATS
        // =====================================================

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            CompactInfoCard(
                modifier = Modifier.weight(1f),
                value = incidentCount.toString(),
                label = "OFFLINE REPORTS",
                onClick = onOfflineReports
            )

            CompactInfoCard(
                modifier = Modifier.weight(1f),
                value = "READY",
                label = "LOCAL STORAGE",
                onClick = onLocalStorage
            )
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "LOW-BANDWIDTH • RESILIENT • EMERGENCY READY",
            color = Color(0xFF98A2B3),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DashboardAction(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {

    Button(
        onClick = onClick,
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF172033)
        )
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = icon,
                fontSize = 20.sp
            )

            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = subtitle,
                fontSize = 8.sp,
                color = Color(0xFF667085)
            )
        }
    }
}


@Composable
fun CompactInfoCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    onClick: () -> Unit
) {

    Column(
        modifier = modifier
            .clickable {
                onClick()
            }
            .background(
                Color.White,
                RoundedCornerShape(14.dp)
            )
            .border(
                1.dp,
                Color(0xFFD9E2EC),
                RoundedCornerShape(14.dp)
            )
            .padding(
                horizontal = 12.dp,
                vertical = 9.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = value,
            color = Color(0xFF1565C0),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = label,
            color = Color(0xFF667085),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
@Composable
fun MeshMessagingScreen(

    connectedNodes: Int,

    messages: List<String>,

    onSendMessage: (String) -> Unit,

    onBack: () -> Unit

) {

    var messageText by remember {
        mutableStateOf("")
    }


    Column(

        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp)

    ) {

        // =====================================================
        // HEADER
        // =====================================================

        Row(

            modifier =
                Modifier.fillMaxWidth(),

            verticalAlignment =
                Alignment.CenterVertically

        ) {

            Button(
                onClick =
                    onBack,

                colors =
                    ButtonDefaults.buttonColors(

                        containerColor =
                            Color(0xFFFFFFFF),

                        contentColor =
                            Color(0xFF172033)
                    )
            ) {

                Text(
                    text =
                        "←"
                )
            }


            Spacer(
                modifier =
                    Modifier.weight(1f)
            )


            Column(
                horizontalAlignment =
                    Alignment.End
            ) {

                Text(

                    text =
                        "MESH MESSAGING",

                    color =
                        Color(0xFF172033),

                    fontSize =
                        21.sp,

                    fontWeight =
                        FontWeight.Bold
                )

                Text(

                    text =
                        "$connectedNodes connected node" +
                                if (
                                    connectedNodes == 1
                                ) {
                                    ""
                                } else {
                                    "s"
                                },

                    color =
                        if (
                            connectedNodes > 0
                        ) {

                            Color(0xFF16A34A)

                        } else {

                            Color(0xFF1565C0)
                        },

                    fontSize =
                        12.sp
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(18.dp)
        )


        // =====================================================
        // INFORMATION
        // =====================================================

        Column(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFFFFFFFF),
                        RoundedCornerShape(15.dp)
                    )
                    .border(
                        1.dp,
                        Color(0xFFD9E2EC),
                        RoundedCornerShape(15.dp)
                    )
                    .padding(15.dp)

        ) {

            Text(

                text =
                    "📡 LOW-BANDWIDTH MESH",

                color =
                    Color(0xFF1565C0),

                fontWeight =
                    FontWeight.Bold,

                fontSize =
                    13.sp
            )

            Spacer(
                modifier =
                    Modifier.height(5.dp)
            )

            Text(

                text =
                    "Messages are stored locally and can " +
                            "travel through connected ResQMesh nodes.",

                color =
                    Color(0xFF667085),

                fontSize =
                    12.sp
            )
        }


        Spacer(
            modifier =
                Modifier.height(15.dp)
        )


        // =====================================================
        // MESSAGES
        // =====================================================

        Text(

            text =
                "MESSAGES",

            color =
                Color(0xFF667085),

            fontSize =
                11.sp,

            fontWeight =
                FontWeight.Bold
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        LazyColumn(

            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),

            verticalArrangement =
                Arrangement.spacedBy(8.dp)

        ) {

            if (messages.isEmpty()) {

                item {

                    Column(

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = 40.dp
                                ),

                        horizontalAlignment =
                            Alignment.CenterHorizontally

                    ) {

                        Text(

                            text =
                                "No messages yet",

                            color =
                                Color(0xFF667085),

                            fontSize =
                                14.sp
                        )

                        Spacer(
                            modifier =
                                Modifier.height(5.dp)
                        )

                        Text(

                            text =
                                "Send a message when another node is connected.",

                            color =
                                Color(0xFF98A2B3),

                            fontSize =
                                11.sp
                        )
                    }
                }

            } else {

                items(messages) { message ->

                    MessageBubble(
                        message =
                            message
                    )
                }
            }
        }


        Spacer(
            modifier =
                Modifier.height(10.dp)
        )


        // =====================================================
        // MESSAGE INPUT
        // =====================================================

        OutlinedTextField(

            value =
                messageText,

            onValueChange = {
                messageText = it
            },

            modifier =
                Modifier.fillMaxWidth(),

            placeholder = {

                Text(
                    text =
                        "Type emergency message..."
                )
            },

            singleLine = false,

            maxLines = 4,

            shape =
                RoundedCornerShape(15.dp)
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        Button(

            onClick = {

                if (
                    messageText.isNotBlank()
                ) {

                    onSendMessage(
                        messageText.trim()
                    )

                    messageText =
                        ""
                }
            },

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),

            enabled =
                messageText.isNotBlank(),

            shape =
                RoundedCornerShape(15.dp),

            colors =
                ButtonDefaults.buttonColors(

                    containerColor =
                        Color(0xFF1565C0),

                    contentColor =
                        Color(0xFFF7F9FC)
                )

        ) {

            Text(

                text =
                    "📨 SEND THROUGH MESH",

                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}


// =============================================================
// MESSAGE BUBBLE
// =============================================================

@Composable
fun MessageBubble(
    message: String
) {

    Column(

        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFFFFFFFF),
                    RoundedCornerShape(14.dp)
                )
                .border(
                    1.dp,
                    Color(0xFFD9E2EC),
                    RoundedCornerShape(14.dp)
                )
                .padding(13.dp)

    ) {

        Text(

            text =
                "📨 INCOMING MESH MESSAGE",

            color =
                Color(0xFF1565C0),

            fontSize =
                10.sp,

            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(5.dp)
        )

        Text(

            text =
                message,

            color =
                Color(0xFF172033),

            fontSize =
                14.sp
        )
    }
}


// =============================================================
// LOCATION SHARE SCREEN
// =============================================================

@Composable
fun LocationShareScreen(
    location: SharedLocation,
    onRoute: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FC))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF172033)
                )
            ) {
                Text(text = "←")
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "LOCATION SHARED",
                color = Color(0xFF172033),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .border(1.dp, Color(0xFFD9E2EC), RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text(
                text = "📍  LOCATION RECEIVED",
                color = Color(0xFF1565C0),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Another ResQMesh device shared its current location with you.",
                color = Color(0xFF667085),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "SHARED DEVICE",
                color = Color(0xFF98A2B3),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = location.senderDeviceId,
                color = Color(0xFF172033),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "COORDINATES",
                color = Color(0xFF98A2B3),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${"%.6f".format(location.latitude)}, ${"%.6f".format(location.longitude)}",
                color = Color(0xFF172033),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onRoute,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1565C0),
                contentColor = Color.White
            )
        ) {
            Text(
                text = "🧭  ROUTE TO THIS LOCATION",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Your current location will be used as the route start.",
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF98A2B3),
            fontSize = 10.sp
        )
    }
}


// =============================================================
// JOIN MESH SCREEN
// =============================================================

@Composable
fun JoinMeshScreen(

    devices: List<BluetoothDevice>,

    onBack: () -> Unit,

    onJoin: (BluetoothDevice) -> Unit

) {

    Column(

        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp)

    ) {

        Text(

            text =
                "JOIN RESQMESH",

            color =
                Color(0xFF172033),

            fontSize =
                28.sp,

            fontWeight =
                FontWeight.Bold
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        Text(

            text =
                "Select a paired ResQMesh device.",

            color =
                Color(0xFF667085),

            fontSize =
                14.sp
        )


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )


        if (devices.isEmpty()) {

            Text(

                text =
                    "No paired Bluetooth devices found.",

                color =
                    Color(0xFF1565C0),

                fontSize =
                    15.sp
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(

                text =
                    "Pair both phones from Android Bluetooth Settings first.",

                color =
                    Color(0xFF667085),

                fontSize =
                    13.sp
            )

        } else {

            devices.forEach { device ->

                DeviceCard(

                    device =
                        device,

                    onJoin = {

                        onJoin(device)
                    }
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )


        Button(

            onClick =
                onBack,

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Text(
                text =
                    "← BACK"
            )
        }
    }
}


// =============================================================
// DEVICE CARD
// =============================================================

@Composable
fun DeviceCard(

    device: BluetoothDevice,

    onJoin: () -> Unit

) {

    Column(

        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFFFFFFFF),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    1.dp,
                    Color(0xFFD9E2EC),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp)

    ) {

        Text(

            text =
                safeDeviceName(device),

            color =
                Color(0xFF172033),

            fontSize =
                17.sp,

            fontWeight =
                FontWeight.Bold
        )


        Spacer(
            modifier =
                Modifier.height(5.dp)
        )


        Text(

            text =
                device.address,

            color =
                Color(0xFF667085),

            fontSize =
                12.sp
        )


        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        Button(

            onClick =
                onJoin,

            modifier =
                Modifier.fillMaxWidth(),

            colors =
                ButtonDefaults.buttonColors(

                    containerColor =
                        Color(0xFF1565C0),

                    contentColor =
                        Color(0xFFF7F9FC)
                )

        ) {

            Text(

                text =
                    "CONNECT TO DEVICE",

                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}


// =============================================================
// INFO CARD
// =============================================================

@Composable
fun InfoCard(

    value: String,

    label: String,

    modifier: Modifier

) {

    Column(

        modifier =
            modifier
                .background(
                    Color(0xFFFFFFFF),
                    RoundedCornerShape(15.dp)
                )
                .border(
                    1.dp,
                    Color(0xFFD9E2EC),
                    RoundedCornerShape(15.dp)
                )
                .padding(15.dp)

    ) {

        Text(

            text =
                value,

            color =
                Color(0xFF1565C0),

            fontSize =
                19.sp,

            fontWeight =
                FontWeight.Bold
        )


        Spacer(
            modifier =
                Modifier.height(3.dp)
        )


        Text(

            text =
                label,

            color =
                Color(0xFF667085),

            fontSize =
                10.sp
        )
    }
}


// =============================================================
// SAFE DEVICE NAME
// =============================================================

fun safeDeviceName(
    device: BluetoothDevice
): String {

    return try {

        device.name
            ?: "Unknown Device"

    } catch (
        e: SecurityException
    ) {

        "Unknown Device"
    }
}


@Composable
fun LocalStorageScreen(
    onBack: () -> Unit
) {

    val context = LocalContext.current

    var incidentCount by remember { mutableStateOf(0) }
    var messageCount by remember { mutableStateOf(0) }
    var packetCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val database = ResQMeshDatabase.getDatabase(context)
        incidentCount = database.incidentDao().getIncidentCount()
        messageCount = database.meshMessageDao().getMessageCount()
        packetCount = database.emergencyPacketDao().getAllPackets().size
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FC))
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "‹",
                color = Color(0xFF0D47A1),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onBack() }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Local Storage",
                    color = Color(0xFF0D47A1),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "ResQMesh local database",
                    color = Color(0xFF667085),
                    fontSize = 11.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0xFFD9E2EC), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LOCAL DATABASE",
                        color = Color(0xFF667085),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "READY",
                        color = Color(0xFF16A34A),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Data is available on this device",
                        color = Color(0xFF667085),
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = "✓",
                    color = Color(0xFF16A34A),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "STORED DATA",
                color = Color(0xFF667085),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            StorageStatCard(
                icon = "📋",
                title = "Incident Reports",
                value = incidentCount.toString(),
                subtitle = "Reports stored locally"
            )

            Spacer(modifier = Modifier.height(10.dp))

            StorageStatCard(
                icon = "💬",
                title = "Mesh Messages",
                value = messageCount.toString(),
                subtitle = "Messages stored locally"
            )

            Spacer(modifier = Modifier.height(10.dp))

            StorageStatCard(
                icon = "🚨",
                title = "Emergency Packets",
                value = packetCount.toString(),
                subtitle = "Emergency data stored locally"
            )
        }
    }
}

@Composable
fun StorageStatCard(
    icon: String,
    title: String,
    value: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFD9E2EC), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 24.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(0xFF172033),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color(0xFF667085),
                fontSize = 10.sp
            )
        }
        Text(
            text = value,
            color = Color(0xFF1565C0),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}


@Composable
fun OfflineReportsScreen(
    onBack: () -> Unit
) {

    val context = LocalContext.current

    var reports by remember {
        mutableStateOf<List<IncidentEntity>>(emptyList())
    }

    LaunchedEffect(Unit) {

        val database =
            ResQMeshDatabase.getDatabase(context)

        reports =
            database
                .incidentDao()
                .getAllIncidents()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FC))
    ) {

        // =====================================================
        // HEADER
        // =====================================================

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(
                    horizontal = 18.dp,
                    vertical = 16.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "‹",
                color = Color(0xFF0D47A1),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable {
                        onBack()
                    }
            )

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            Column {

                Text(
                    text = "Offline Reports",
                    color = Color(0xFF0D47A1),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = "${reports.size} reports stored locally",
                    color = Color(0xFF667085),
                    fontSize = 11.sp
                )
            }
        }

        // =====================================================
        // REPORT LIST
        // =====================================================

        if (reports.isEmpty()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = "📋",
                    fontSize = 42.sp
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "No offline reports",
                    color = Color(0xFF172033),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Text(
                    text = "Reports created on this device will appear here.",
                    color = Color(0xFF667085),
                    fontSize = 12.sp
                )
            }

        } else {

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 14.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                items(
                    items = reports,
                    key = { it.id }
                ) { report ->

                    OfflineReportCard(
                        report = report
                    )
                }
            }
        }
    }
}


@Composable
fun OfflineReportCard(
    report: IncidentEntity
) {

    val severityColor = when (report.severity.uppercase()) {
        "CRITICAL" -> Color(0xFFD32F2F)
        "HIGH" -> Color(0xFFE65100)
        "MEDIUM" -> Color(0xFFF9A825)
        else -> Color(0xFF16A34A)
    }

    val icon = when (report.type.uppercase()) {
        "FIRE" -> "🔥"
        "FLOOD" -> "🌊"
        "ROAD_BLOCKED" -> "🚧"
        "ACCIDENT" -> "🚑"
        "BUILDING_COLLAPSE" -> "🏚️"
        "MEDICAL_EMERGENCY" -> "🏥"
        "DANGEROUS_AREA" -> "⚠️"
        "MISSING_PERSON" -> "🔎"
        "PEOPLE_TRAPPED" -> "🆘"
        "NATURAL_DISASTER" -> "🌪️"
        else -> "📍"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.White,
                RoundedCornerShape(16.dp)
            )
            .border(
                1.dp,
                Color(0xFFD9E2EC),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = icon,
                fontSize = 27.sp
            )

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = report.type.replace("_", " "),
                    color = Color(0xFF172033),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(3.dp)
                )

                Text(
                    text = report.description,
                    color = Color(0xFF667085),
                    fontSize = 11.sp,
                    maxLines = 2
                )
            }

            Text(
                text = report.severity,
                color = severityColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        HorizontalDivider(
            color = Color(0xFFE4E7EC)
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                text = if (report.verified)
                    "✓ VERIFIED"
                else
                    "○ UNVERIFIED",
                color = if (report.verified)
                    Color(0xFF16A34A)
                else
                    Color(0xFF667085),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (
                    report.latitude != null &&
                    report.longitude != null
                ) {
                    "📍 LOCATION AVAILABLE"
                } else {
                    "📍 NO LOCATION"
                },
                color = Color(0xFF667085),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}











//
//
//
//
//
//package com.resqmesh.app
//
//// mport androidx.compose.foundation.layout.weight
//
//import android.Manifest
//import android.bluetooth.BluetoothDevice
//import android.content.pm.PackageManager
//import android.content.Intent
//import android.location.Location
//import android.net.Uri
//import android.os.Build
//import com.resqmesh.app.network.SharedLocation
//import android.os.Bundle
//import android.util.Log
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.rememberLauncherForActivityResult
//import androidx.activity.compose.setContent
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.background
//import androidx.compose.foundation.border
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.Button
//import androidx.compose.material3.ButtonDefaults
//import androidx.compose.material3.OutlinedTextField
//import androidx.compose.material3.Surface
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateListOf
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.core.content.ContextCompat
//import com.google.android.gms.location.LocationServices
//import com.google.android.gms.location.Priority
//import com.google.android.gms.tasks.CancellationTokenSource
//import com.resqmesh.app.database.ResQMeshDatabase
//import com.resqmesh.app.model.EmergencyPacketEntity
//import com.resqmesh.app.network.NearbyMeshManager
//import com.resqmesh.app.ui.EmergencyScreen
//import com.resqmesh.app.ui.IncomingEmergencyScreen
//import com.resqmesh.app.ui.theme.MapScreen
//import com.resqmesh.app.ui.ReportIncidentScreen
//import com.resqmesh.app.ui.theme.ResQMeshTheme
//
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//
//import androidx.compose.material3.HorizontalDivider
//
//
//// =============================================================
//// MAIN ACTIVITY
//// =============================================================
//
//class MainActivity : ComponentActivity() {
//
//    override fun onCreate(
//        savedInstanceState: Bundle?
//    ) {
//
//        super.onCreate(savedInstanceState)
//
//        setContent {
//
//            ResQMeshTheme {
//
//                ResQMeshApp()
//            }
//        }
//    }
//}
//
//
//// =============================================================
//// APP
//// =============================================================
//
//@Composable
//fun ResQMeshApp() {
//
//    val context =
//        LocalContext.current
//
//    val nearbyMeshManager =
//        remember {
//            NearbyMeshManager(context)
//        }
//
//    var nearbyDeviceCount by remember {
//        mutableStateOf(0)
//    }
//
//    var meshActive by remember {
//        mutableStateOf(false)
//    }
//
//    var incomingEmergencyPacket by remember {
//        mutableStateOf<EmergencyPacketEntity?>(null)
//    }
//    var sharedLocation by remember {
//        mutableStateOf<SharedLocation?>(null)
//    }
//
//    var showLocationShareScreen by remember {
//        mutableStateOf(false)
//    }
//
//    var showReportScreen by remember {
//        mutableStateOf(false)
//    }
//
//    var showEmergencyScreen by remember {
//        mutableStateOf(false)
//    }
//
//    var showJoinScreen by remember {
//        mutableStateOf(false)
//    }
//
//    var showMapScreen by remember { mutableStateOf(false) }
//
//    var showMessagingScreen by remember {
//        mutableStateOf(false)
//    }
//
//    var pairedDevices by remember {
//        mutableStateOf<List<BluetoothDevice>>(emptyList())
//    }
//
//    /*
//     * =========================================================
//     * MESH MESSAGE STATE
//     * =========================================================
//     */
//
//    val receivedMessages =
//        remember {
//            mutableStateListOf<String>()
//        }
//
//
//    // =========================================================
//    // CALLBACKS
//    // =========================================================
//
//    LaunchedEffect(Unit) {
//
//        nearbyMeshManager.onDeviceCountChanged =
//            { count ->
//
//                nearbyDeviceCount =
//                    count
//
//                Log.d(
//                    "ResQMeshBluetooth",
//                    "CONNECTED RESQMESH NODES = $count"
//                )
//            }
//
//
//        nearbyMeshManager.onEmergencyPacketReceived =
//            { packet ->
//
//                Log.d(
//                    "ResQMeshBluetooth",
//                    "INCOMING SOS = ${packet.packetId}"
//                )
//
//                incomingEmergencyPacket =
//                    packet
//            }
//
//
//        nearbyMeshManager.onLocationReceived =
//            { location ->
//
//                Log.d(
//                    "ResQMeshBluetooth",
//                    "INCOMING SHARED LOCATION = ${location.latitude}, ${location.longitude}"
//                )
//
//                sharedLocation = location
//                showLocationShareScreen = true
//            }
//
//
//        /*
//         * Normal mesh message callback.
//         */
//        nearbyMeshManager.onMessageReceived =
//            { message ->
//
//                Log.d(
//                    "ResQMeshBluetooth",
//                    "INCOMING MESH MESSAGE = $message"
//                )
//
//                receivedMessages.add(
//                    message
//                )
//            }
//    }
//
//
//    // =========================================================
//    // PERMISSIONS
//    // =========================================================
//
//    val nearbyPermissions =
//        if (
//            Build.VERSION.SDK_INT >=
//            Build.VERSION_CODES.TIRAMISU
//        ) {
//
//            arrayOf(
//                Manifest.permission.BLUETOOTH_SCAN,
//                Manifest.permission.BLUETOOTH_CONNECT,
//                Manifest.permission.BLUETOOTH_ADVERTISE,
//                Manifest.permission.NEARBY_WIFI_DEVICES,
//                Manifest.permission.ACCESS_FINE_LOCATION,
//                Manifest.permission.ACCESS_COARSE_LOCATION
//            )
//
//        } else if (
//            Build.VERSION.SDK_INT >=
//            Build.VERSION_CODES.S
//        ) {
//
//            arrayOf(
//                Manifest.permission.BLUETOOTH_SCAN,
//                Manifest.permission.BLUETOOTH_CONNECT,
//                Manifest.permission.BLUETOOTH_ADVERTISE,
//                Manifest.permission.ACCESS_FINE_LOCATION,
//                Manifest.permission.ACCESS_COARSE_LOCATION
//            )
//
//        } else {
//
//            arrayOf(
//                Manifest.permission.ACCESS_FINE_LOCATION,
//                Manifest.permission.ACCESS_COARSE_LOCATION
//            )
//        }
//
//
//    // =========================================================
//    // CREATE MESH PERMISSION
//    // =========================================================
//
//    val createMeshLauncher =
//        rememberLauncherForActivityResult(
//            ActivityResultContracts.RequestMultiplePermissions()
//        ) { permissions ->
//
//            val allGranted =
//                nearbyPermissions.all { permission ->
//
//                    permissions[permission] == true ||
//                            ContextCompat.checkSelfPermission(
//                                context,
//                                permission
//                            ) ==
//                            PackageManager.PERMISSION_GRANTED
//                }
//
//
//            if (allGranted) {
//
//                Log.d(
//                    "ResQMeshBluetooth",
//                    "ALL BLUETOOTH PERMISSIONS GRANTED"
//                )
//
//                meshActive =
//                    true
//
//                nearbyMeshManager.startMesh()
//
//            } else {
//
//                Log.e(
//                    "ResQMeshBluetooth",
//                    "BLUETOOTH PERMISSIONS DENIED"
//                )
//            }
//        }
//
//
//    // =========================================================
//    // JOIN MESH PERMISSION
//    // =========================================================
//
//    val joinMeshLauncher =
//        rememberLauncherForActivityResult(
//            ActivityResultContracts.RequestMultiplePermissions()
//        ) { permissions ->
//
//            val allGranted =
//                nearbyPermissions.all { permission ->
//
//                    permissions[permission] == true ||
//                            ContextCompat.checkSelfPermission(
//                                context,
//                                permission
//                            ) ==
//                            PackageManager.PERMISSION_GRANTED
//                }
//
//
//            if (allGranted) {
//
//                Log.d(
//                    "ResQMeshBluetooth",
//                    "ALL BLUETOOTH PERMISSIONS GRANTED"
//                )
//
//                pairedDevices =
//                    nearbyMeshManager.getPairedDevices()
//
//                Log.d(
//                    "ResQMeshBluetooth",
//                    "PAIRED DEVICES = ${pairedDevices.size}"
//                )
//
//                showJoinScreen =
//                    true
//
//            } else {
//
//                Log.e(
//                    "ResQMeshBluetooth",
//                    "BLUETOOTH PERMISSIONS DENIED"
//                )
//            }
//        }
//
//
//    // =========================================================
//    // LOCATION SHARING
//    // =========================================================
//
//    val fusedLocationClient = remember {
//        LocationServices.getFusedLocationProviderClient(context)
//    }
//
//    fun hasLocationPermission(): Boolean {
//        return ContextCompat.checkSelfPermission(
//            context,
//            Manifest.permission.ACCESS_FINE_LOCATION
//        ) == PackageManager.PERMISSION_GRANTED ||
//                ContextCompat.checkSelfPermission(
//                    context,
//                    Manifest.permission.ACCESS_COARSE_LOCATION
//                ) == PackageManager.PERMISSION_GRANTED
//    }
//
//    fun shareCurrentLocation() {
//        if (!hasLocationPermission()) {
//            Log.e("ResQMeshLocation", "LOCATION PERMISSION NOT GRANTED")
//            return
//        }
//
//        fusedLocationClient.getCurrentLocation(
//            Priority.PRIORITY_HIGH_ACCURACY,
//            CancellationTokenSource().token
//        ).addOnSuccessListener { location: Location? ->
//            if (location == null) {
//                Log.e("ResQMeshLocation", "CURRENT LOCATION UNAVAILABLE")
//                fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
//                    if (lastLocation != null) {
//                        nearbyMeshManager.sendLocationShare(
//                            lastLocation.latitude,
//                            lastLocation.longitude
//                        )
//                    }
//                }
//                return@addOnSuccessListener
//            }
//
//            Log.d(
//                "ResQMeshLocation",
//                "SHARING CURRENT LOCATION = ${location.latitude}, ${location.longitude}"
//            )
//
//            nearbyMeshManager.sendLocationShare(
//                location.latitude,
//                location.longitude
//            )
//        }.addOnFailureListener { error ->
//            Log.e("ResQMeshLocation", "LOCATION FETCH FAILED", error)
//        }
//    }
//
//    val locationPermissionLauncher =
//        rememberLauncherForActivityResult(
//            ActivityResultContracts.RequestMultiplePermissions()
//        ) { permissions ->
//            val granted =
//                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
//                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
//                        hasLocationPermission()
//
//            if (granted) {
//                shareCurrentLocation()
//            } else {
//                Log.e("ResQMeshLocation", "LOCATION PERMISSION DENIED")
//            }
//        }
//
//    fun requestAndShareLocation() {
//        if (hasLocationPermission()) {
//            shareCurrentLocation()
//        } else {
//            locationPermissionLauncher.launch(
//                arrayOf(
//                    Manifest.permission.ACCESS_FINE_LOCATION,
//                    Manifest.permission.ACCESS_COARSE_LOCATION
//                )
//            )
//        }
//    }
//
//    // =========================================================
//    // MAIN ROUTING
//    // =========================================================
//
//    Surface(
//        modifier =
//            Modifier.fillMaxSize(),
//
//        color =
//            Color(0xFFF7F9FC)
//    ) {
//
//        when {
//
//            // -------------------------------------------------
//            // INCOMING SOS
//            // -------------------------------------------------
//
//            incomingEmergencyPacket != null -> {
//
//                IncomingEmergencyScreen(
//
//                    packet =
//                        incomingEmergencyPacket!!,
//
//                    onAcknowledge = {
//
//                        incomingEmergencyPacket =
//                            null
//                    }
//                )
//            }
//
//
//            // -------------------------------------------------
//            // INCOMING SHARED LOCATION
//            // -------------------------------------------------
//
//            showLocationShareScreen && sharedLocation != null -> {
//                LocationShareScreen(
//                    location = sharedLocation!!,
//                    onRoute = {
//                        val target = sharedLocation!!
//
//                        if (!hasLocationPermission()) {
//                            locationPermissionLauncher.launch(
//                                arrayOf(
//                                    Manifest.permission.ACCESS_FINE_LOCATION,
//                                    Manifest.permission.ACCESS_COARSE_LOCATION
//                                )
//                            )
//                        } else {
//                            fusedLocationClient.getCurrentLocation(
//                                Priority.PRIORITY_HIGH_ACCURACY,
//                                CancellationTokenSource().token
//                            ).addOnSuccessListener { current ->
//                                if (current != null) {
//                                    val uri = Uri.parse(
//                                        "https://www.google.com/maps/dir/?api=1" +
//                                                "&origin=${current.latitude},${current.longitude}" +
//                                                "&destination=${target.latitude},${target.longitude}" +
//                                                "&travelmode=driving"
//                                    )
//                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
//                                }
//                            }
//                        }
//                    },
//                    onDismiss = {
//                        showLocationShareScreen = false
//                        sharedLocation = null
//                    }
//                )
//            }
//
//            // -------------------------------------------------
//            // EMERGENCY
//            // -------------------------------------------------
//
//            showEmergencyScreen -> {
//
//                EmergencyScreen(
//
//                    onCancel = {
//
//                        showEmergencyScreen =
//                            false
//                    }
//                )
//            }
//
//
//            // -------------------------------------------------
//            // REPORT
//            // -------------------------------------------------
//
//            showReportScreen -> {
//
//                ReportIncidentScreen()
//            }
//
//            showMapScreen -> {
//                MapScreen()
//            }
//
//
//            // -------------------------------------------------
//            // JOIN
//            // -------------------------------------------------
//
//            showJoinScreen -> {
//
//                JoinMeshScreen(
//
//                    devices =
//                        pairedDevices,
//
//                    onBack = {
//
//                        showJoinScreen =
//                            false
//                    },
//
//                    onJoin = { device ->
//
//                        Log.d(
//                            "ResQMeshBluetooth",
//                            "JOINING SELECTED DEVICE"
//                        )
//
//                        meshActive =
//                            true
//
//                        showJoinScreen =
//                            false
//
//                        nearbyMeshManager.joinMesh(
//                            device
//                        )
//                    }
//                )
//            }
//
//
//            // -------------------------------------------------
//            // MESSAGING
//            // -------------------------------------------------
//
//            showMessagingScreen -> {
//
//                MeshMessagingScreen(
//
//                    connectedNodes =
//                        nearbyDeviceCount,
//
//                    messages =
//                        receivedMessages,
//
//                    onSendMessage = { message ->
//
//                        nearbyMeshManager
//                            .sendTextMessage(
//                                message
//                            )
//                    },
//
//                    onBack = {
//
//                        showMessagingScreen =
//                            false
//                    }
//                )
//            }
//
//
//            // -------------------------------------------------
//            // HOME
//            // -------------------------------------------------
//
//            else ->
//
//                "offline_reports" -> {
//            OfflineReportsScreen(
//                onBack = {
//                    currentScreen = "home"
//                }
//            )
//        }
//
//                {
//
//                HomeScreen(
//
//                    onCreateMesh = {
//                        Log.d(
//                            "ResQMeshBluetooth",
//                            "REQUESTING HOST PERMISSIONS"
//                        )
//
//                        createMeshLauncher.launch(
//                            nearbyPermissions
//                        )
//                    },
//
//                    onJoinMesh = {
//                        Log.d(
//                            "ResQMeshBluetooth",
//                            "REQUESTING JOIN PERMISSIONS"
//                        )
//
//                        joinMeshLauncher.launch(
//                            nearbyPermissions
//                        )
//                    },
//
//                    onReportIncident = {
//                        showReportScreen = true
//                    },
//
//                    onEmergency = {
//                        showEmergencyScreen = true
//                    },
//
//                    onMap = {
//                        showMapScreen = true
//                    },
//
//                    onMessaging = {
//                        showMessagingScreen = true
//                    },
//
//                    onShareLocation = {
//                        requestAndShareLocation()
//                    },
//
//                    onOfflineReports = {
//                        currentScreen = "offline_reports"
//                    },
//
//                    onLocalStorage = {
//                        currentScreen = "local_storage"
//                    },
//
//                    nearbyDeviceCount =
//                        nearbyDeviceCount,
//
//                    meshActive =
//                        meshActive
//                )
//
//
//            }
//        }
//    }
//}
//
//
//// =============================================================
//// HOME SCREEN
//// =============================================================
//
//@Composable
//fun HomeScreen(
//    onMap: () -> Unit,
//    onCreateMesh: () -> Unit,
//    onJoinMesh: () -> Unit,
//    onReportIncident: () -> Unit,
//    onEmergency: () -> Unit,
//    onMessaging: () -> Unit,
//    onShareLocation: () -> Unit,
//    onOfflineReports: () -> Unit,
//    onLocalStorage: () -> Unit,
//    nearbyDeviceCount: Int,
//    meshActive: Boolean
//) {
//
//    val context = LocalContext.current
//
//    var incidentCount by remember {
//        mutableStateOf(0)
//    }
//
//    LaunchedEffect(Unit) {
//
//        val database =
//            ResQMeshDatabase.getDatabase(context)
//
//        incidentCount =
//            database
//                .incidentDao()
//                .getIncidentCount()
//    }
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(Color(0xFFF7F9FC))
//            .padding(
//                horizontal = 18.dp,
//                vertical = 14.dp
//            ),
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//
//        // =====================================================
//        // HEADER
//        // =====================================================
//
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//
//            Column(
//                modifier = Modifier.weight(1f)
//            ) {
//
//                Text(
//                    text = "ResQMesh",
//                    color = Color(0xFF0D47A1),
//                    fontSize = 28.sp,
//                    fontWeight = FontWeight.ExtraBold
//                )
//
//                Text(
//                    text = "Emergency Response Network",
//                    color = Color(0xFF667085),
//                    fontSize = 12.sp
//                )
//            }
//
//            Column(
//                horizontalAlignment = Alignment.End
//            ) {
//
//                Text(
//                    text = if (meshActive) "● ONLINE" else "● STANDBY",
//                    color = if (meshActive)
//                        Color(0xFF16A34A)
//                    else
//                        Color(0xFF667085),
//                    fontSize = 11.sp,
//                    fontWeight = FontWeight.Bold
//                )
//
//                Text(
//                    text = "$nearbyDeviceCount NODE" +
//                            if (nearbyDeviceCount == 1) "" else "S",
//                    color = Color(0xFF667085),
//                    fontSize = 10.sp
//                )
//            }
//        }
//
//        Spacer(
//            modifier = Modifier.height(12.dp)
//        )
//
//        // =====================================================
//        // NETWORK STATUS CARD
//        // =====================================================
//
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .background(
//                    Color.White,
//                    RoundedCornerShape(16.dp)
//                )
//                .border(
//                    1.dp,
//                    Color(0xFFD9E2EC),
//                    RoundedCornerShape(16.dp)
//                )
//                .padding(
//                    horizontal = 15.dp,
//                    vertical = 12.dp
//                ),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//
//            Column(
//                modifier = Modifier.weight(1f)
//            ) {
//
//                Text(
//                    text = "NETWORK STATUS",
//                    color = Color(0xFF667085),
//                    fontSize = 9.sp,
//                    fontWeight = FontWeight.Bold
//                )
//
//                Spacer(
//                    modifier = Modifier.height(2.dp)
//                )
//
//                Text(
//                    text = if (meshActive)
//                        "Mesh network active"
//                    else
//                        "Ready to establish network",
//                    color = Color(0xFF172033),
//                    fontSize = 13.sp,
//                    fontWeight = FontWeight.SemiBold
//                )
//            }
//
//            Text(
//                text = "$nearbyDeviceCount\nNODES",
//                color = Color(0xFF1565C0),
//                fontSize = 12.sp,
//                fontWeight = FontWeight.Bold
//            )
//        }
//
//        Spacer(
//            modifier = Modifier.height(14.dp)
//        )
//
//        // =====================================================
//        // MAIN ACTION GRID
//        // =====================================================
//
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.spacedBy(10.dp)
//        ) {
//
//            DashboardAction(
//                modifier = Modifier.weight(1f),
//                icon = "📡",
//                title = "CREATE MESH",
//                subtitle = "Start network",
//                onClick = onCreateMesh
//            )
//
//            DashboardAction(
//                modifier = Modifier.weight(1f),
//                icon = "🔗",
//                title = "JOIN MESH",
//                subtitle = "Connect node",
//                onClick = onJoinMesh
//            )
//        }
//
//        Spacer(
//            modifier = Modifier.height(10.dp)
//        )
//
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.spacedBy(10.dp)
//        ) {
//
//            DashboardAction(
//                modifier = Modifier.weight(1f),
//                icon = "💬",
//                title = "MESSAGING",
//                subtitle = "Mesh messages",
//                onClick = onMessaging
//            )
//
//            DashboardAction(
//                modifier = Modifier.weight(1f),
//                icon = "🗺️",
//                title = "MAP",
//                subtitle = "Live location",
//                onClick = onMap
//            )
//        }
//
//        Spacer(
//            modifier = Modifier.height(12.dp)
//        )
//
//        Button(
//            onClick = onShareLocation,
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(44.dp),
//            shape = RoundedCornerShape(14.dp),
//            colors = ButtonDefaults.buttonColors(
//                containerColor = Color(0xFFEAF2FF),
//                contentColor = Color(0xFF0D47A1)
//            )
//        ) {
//            Text(
//                text = "📍  SHARE MY LOCATION",
//                fontSize = 12.sp,
//                fontWeight = FontWeight.ExtraBold
//            )
//        }
//
//        Spacer(
//            modifier = Modifier.height(8.dp)
//        )
//
//        // =====================================================
//        // SOS
//        // =====================================================
//
//        Button(
//            onClick = onEmergency,
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(58.dp),
//            shape = RoundedCornerShape(16.dp),
//            colors = ButtonDefaults.buttonColors(
//                containerColor = Color(0xFFD32F2F),
//                contentColor = Color.White
//            )
//        ) {
//
//            Column(
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//
//                Text(
//                    text = "🚨  I'M IN DANGER",
//                    fontSize = 15.sp,
//                    fontWeight = FontWeight.ExtraBold
//                )
//
//                Text(
//                    text = "Send emergency alert",
//                    fontSize = 10.sp
//                )
//            }
//        }
//
//        Spacer(
//            modifier = Modifier.height(10.dp)
//        )
//
//        // =====================================================
//        // REPORT INCIDENT
//        // =====================================================
//
//        Button(
//            onClick = onReportIncident,
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(50.dp),
//            shape = RoundedCornerShape(14.dp),
//            colors = ButtonDefaults.buttonColors(
//                containerColor = Color.White,
//                contentColor = Color(0xFF172033)
//            )
//        ) {
//
//            Text(
//                text = "🚧  REPORT INCIDENT",
//                fontWeight = FontWeight.Bold,
//                fontSize = 13.sp
//            )
//        }
//
//        Spacer(
//            modifier = Modifier.height(10.dp)
//        )
//
//        // =====================================================
//        // QUICK STATS
//        // =====================================================
//
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.spacedBy(10.dp)
//        ) {
//
//            CompactInfoCard(
//                modifier = Modifier.weight(1f),
//                value = incidentCount.toString(),
//                label = "OFFLINE REPORTS",
//                onClick = onOfflineReports
//            )
//
//            CompactInfoCard(
//                modifier = Modifier.weight(1f),
//                value = "READY",
//                label = "LOCAL STORAGE",
//                onClick = TODO(),
//                onClick = onLocalStorage
//            )
//        }
//
//        Spacer(
//            modifier = Modifier.height(8.dp)
//        )
//
//        Text(
//            text = "LOW-BANDWIDTH • RESILIENT • EMERGENCY READY",
//            color = Color(0xFF98A2B3),
//            fontSize = 8.sp,
//            fontWeight = FontWeight.Bold
//        )
//    }
//}
//
//@Composable
//fun DashboardAction(
//    modifier: Modifier = Modifier,
//    icon: String,
//    title: String,
//    subtitle: String,
//    onClick: () -> Unit
//) {
//
//    Button(
//        onClick = onClick,
//        modifier = modifier.height(78.dp),
//        shape = RoundedCornerShape(16.dp),
//        colors = ButtonDefaults.buttonColors(
//            containerColor = Color.White,
//            contentColor = Color(0xFF172033)
//        )
//    ) {
//
//        Column(
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//
//            Text(
//                text = icon,
//                fontSize = 20.sp
//            )
//
//            Text(
//                text = title,
//                fontSize = 11.sp,
//                fontWeight = FontWeight.ExtraBold
//            )
//
//            Text(
//                text = subtitle,
//                fontSize = 8.sp,
//                color = Color(0xFF667085)
//            )
//        }
//    }
//}
//
//
//@Composable
//fun CompactInfoCard(
//    modifier: Modifier = Modifier,
//    value: String,
//    label: String,
//    onClick: () -> Unit
//) {
//
//    Column(
//        modifier = modifier
//            .clickable {
//                onClick()
//            }
//            .background(
//                Color.White,
//                RoundedCornerShape(14.dp)
//            )
//            .border(
//                1.dp,
//                Color(0xFFD9E2EC),
//                RoundedCornerShape(14.dp)
//            )
//            .padding(
//                horizontal = 12.dp,
//                vertical = 9.dp
//            ),
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//
//        Text(
//            text = value,
//            color = Color(0xFF1565C0),
//            fontSize = 16.sp,
//            fontWeight = FontWeight.ExtraBold
//        )
//
//        Text(
//            text = label,
//            color = Color(0xFF667085),
//            fontSize = 8.sp,
//            fontWeight = FontWeight.Bold
//        )
//    }
//}
//@Composable
//fun MeshMessagingScreen(
//
//    connectedNodes: Int,
//
//    messages: List<String>,
//
//    onSendMessage: (String) -> Unit,
//
//    onBack: () -> Unit
//
//) {
//
//    var messageText by remember {
//        mutableStateOf("")
//    }
//
//
//    Column(
//
//        modifier =
//            Modifier
//                .fillMaxSize()
//                .padding(20.dp)
//
//    ) {
//
//        // =====================================================
//        // HEADER
//        // =====================================================
//
//        Row(
//
//            modifier =
//                Modifier.fillMaxWidth(),
//
//            verticalAlignment =
//                Alignment.CenterVertically
//
//        ) {
//
//            Button(
//                onClick =
//                    onBack,
//
//                colors =
//                    ButtonDefaults.buttonColors(
//
//                        containerColor =
//                            Color(0xFFFFFFFF),
//
//                        contentColor =
//                            Color(0xFF172033)
//                    )
//            ) {
//
//                Text(
//                    text =
//                        "←"
//                )
//            }
//
//
//            Spacer(
//                modifier =
//                    Modifier.weight(1f)
//            )
//
//
//            Column(
//                horizontalAlignment =
//                    Alignment.End
//            ) {
//
//                Text(
//
//                    text =
//                        "MESH MESSAGING",
//
//                    color =
//                        Color(0xFF172033),
//
//                    fontSize =
//                        21.sp,
//
//                    fontWeight =
//                        FontWeight.Bold
//                )
//
//                Text(
//
//                    text =
//                        "$connectedNodes connected node" +
//                                if (
//                                    connectedNodes == 1
//                                ) {
//                                    ""
//                                } else {
//                                    "s"
//                                },
//
//                    color =
//                        if (
//                            connectedNodes > 0
//                        ) {
//
//                            Color(0xFF16A34A)
//
//                        } else {
//
//                            Color(0xFF1565C0)
//                        },
//
//                    fontSize =
//                        12.sp
//                )
//            }
//        }
//
//
//        Spacer(
//            modifier =
//                Modifier.height(18.dp)
//        )
//
//
//        // =====================================================
//        // INFORMATION
//        // =====================================================
//
//        Column(
//
//            modifier =
//                Modifier
//                    .fillMaxWidth()
//                    .background(
//                        Color(0xFFFFFFFF),
//                        RoundedCornerShape(15.dp)
//                    )
//                    .border(
//                        1.dp,
//                        Color(0xFFD9E2EC),
//                        RoundedCornerShape(15.dp)
//                    )
//                    .padding(15.dp)
//
//        ) {
//
//            Text(
//
//                text =
//                    "📡 LOW-BANDWIDTH MESH",
//
//                color =
//                    Color(0xFF1565C0),
//
//                fontWeight =
//                    FontWeight.Bold,
//
//                fontSize =
//                    13.sp
//            )
//
//            Spacer(
//                modifier =
//                    Modifier.height(5.dp)
//            )
//
//            Text(
//
//                text =
//                    "Messages are stored locally and can " +
//                            "travel through connected ResQMesh nodes.",
//
//                color =
//                    Color(0xFF667085),
//
//                fontSize =
//                    12.sp
//            )
//        }
//
//
//        Spacer(
//            modifier =
//                Modifier.height(15.dp)
//        )
//
//
//        // =====================================================
//        // MESSAGES
//        // =====================================================
//
//        Text(
//
//            text =
//                "MESSAGES",
//
//            color =
//                Color(0xFF667085),
//
//            fontSize =
//                11.sp,
//
//            fontWeight =
//                FontWeight.Bold
//        )
//
//
//        Spacer(
//            modifier =
//                Modifier.height(8.dp)
//        )
//
//
//        LazyColumn(
//
//            modifier =
//                Modifier
//                    .weight(1f)
//                    .fillMaxWidth(),
//
//            verticalArrangement =
//                Arrangement.spacedBy(8.dp)
//
//        ) {
//
//            if (messages.isEmpty()) {
//
//                item {
//
//                    Column(
//
//                        modifier =
//                            Modifier
//                                .fillMaxWidth()
//                                .padding(
//                                    top = 40.dp
//                                ),
//
//                        horizontalAlignment =
//                            Alignment.CenterHorizontally
//
//                    ) {
//
//                        Text(
//
//                            text =
//                                "No messages yet",
//
//                            color =
//                                Color(0xFF667085),
//
//                            fontSize =
//                                14.sp
//                        )
//
//                        Spacer(
//                            modifier =
//                                Modifier.height(5.dp)
//                        )
//
//                        Text(
//
//                            text =
//                                "Send a message when another node is connected.",
//
//                            color =
//                                Color(0xFF98A2B3),
//
//                            fontSize =
//                                11.sp
//                        )
//                    }
//                }
//
//            } else {
//
//                items(messages) { message ->
//
//                    MessageBubble(
//                        message =
//                            message
//                    )
//                }
//            }
//        }
//
//
//        Spacer(
//            modifier =
//                Modifier.height(10.dp)
//        )
//
//
//        // =====================================================
//        // MESSAGE INPUT
//        // =====================================================
//
//        OutlinedTextField(
//
//            value =
//                messageText,
//
//            onValueChange = {
//                messageText = it
//            },
//
//            modifier =
//                Modifier.fillMaxWidth(),
//
//            placeholder = {
//
//                Text(
//                    text =
//                        "Type emergency message..."
//                )
//            },
//
//            singleLine = false,
//
//            maxLines = 4,
//
//            shape =
//                RoundedCornerShape(15.dp)
//        )
//
//
//        Spacer(
//            modifier =
//                Modifier.height(8.dp)
//        )
//
//
//        Button(
//
//            onClick = {
//
//                if (
//                    messageText.isNotBlank()
//                ) {
//
//                    onSendMessage(
//                        messageText.trim()
//                    )
//
//                    messageText =
//                        ""
//                }
//            },
//
//            modifier =
//                Modifier
//                    .fillMaxWidth()
//                    .height(52.dp),
//
//            enabled =
//                messageText.isNotBlank(),
//
//            shape =
//                RoundedCornerShape(15.dp),
//
//            colors =
//                ButtonDefaults.buttonColors(
//
//                    containerColor =
//                        Color(0xFF1565C0),
//
//                    contentColor =
//                        Color(0xFFF7F9FC)
//                )
//
//        ) {
//
//            Text(
//
//                text =
//                    "📨 SEND THROUGH MESH",
//
//                fontWeight =
//                    FontWeight.Bold
//            )
//        }
//    }
//}
//
//
//// =============================================================
//// MESSAGE BUBBLE
//// =============================================================
//
//@Composable
//fun MessageBubble(
//    message: String
//) {
//
//    Column(
//
//        modifier =
//            Modifier
//                .fillMaxWidth()
//                .background(
//                    Color(0xFFFFFFFF),
//                    RoundedCornerShape(14.dp)
//                )
//                .border(
//                    1.dp,
//                    Color(0xFFD9E2EC),
//                    RoundedCornerShape(14.dp)
//                )
//                .padding(13.dp)
//
//    ) {
//
//        Text(
//
//            text =
//                "📨 INCOMING MESH MESSAGE",
//
//            color =
//                Color(0xFF1565C0),
//
//            fontSize =
//                10.sp,
//
//            fontWeight =
//                FontWeight.Bold
//        )
//
//        Spacer(
//            modifier =
//                Modifier.height(5.dp)
//        )
//
//        Text(
//
//            text =
//                message,
//
//            color =
//                Color(0xFF172033),
//
//            fontSize =
//                14.sp
//        )
//    }
//}
//
//
//// =============================================================
//// LOCATION SHARE SCREEN
//// =============================================================
//
//@Composable
//fun LocationShareScreen(
//    location: SharedLocation,
//    onRoute: () -> Unit,
//    onDismiss: () -> Unit
//) {
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(Color(0xFFF7F9FC))
//            .padding(20.dp)
//    ) {
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Button(
//                onClick = onDismiss,
//                colors = ButtonDefaults.buttonColors(
//                    containerColor = Color.White,
//                    contentColor = Color(0xFF172033)
//                )
//            ) {
//                Text(text = "←")
//            }
//
//            Spacer(modifier = Modifier.weight(1f))
//
//            Text(
//                text = "LOCATION SHARED",
//                color = Color(0xFF172033),
//                fontSize = 21.sp,
//                fontWeight = FontWeight.Bold
//            )
//        }
//
//        Spacer(modifier = Modifier.height(22.dp))
//
//        Column(
//            modifier = Modifier
//                .fillMaxWidth()
//                .background(Color.White, RoundedCornerShape(18.dp))
//                .border(1.dp, Color(0xFFD9E2EC), RoundedCornerShape(18.dp))
//                .padding(18.dp)
//        ) {
//            Text(
//                text = "📍  LOCATION RECEIVED",
//                color = Color(0xFF1565C0),
//                fontSize = 13.sp,
//                fontWeight = FontWeight.ExtraBold
//            )
//
//            Spacer(modifier = Modifier.height(8.dp))
//
//            Text(
//                text = "Another ResQMesh device shared its current location with you.",
//                color = Color(0xFF667085),
//                fontSize = 13.sp
//            )
//
//            Spacer(modifier = Modifier.height(18.dp))
//
//            Text(
//                text = "SHARED DEVICE",
//                color = Color(0xFF98A2B3),
//                fontSize = 9.sp,
//                fontWeight = FontWeight.Bold
//            )
//
//            Text(
//                text = location.senderDeviceId,
//                color = Color(0xFF172033),
//                fontSize = 13.sp,
//                fontWeight = FontWeight.SemiBold
//            )
//
//            Spacer(modifier = Modifier.height(12.dp))
//
//            Text(
//                text = "COORDINATES",
//                color = Color(0xFF98A2B3),
//                fontSize = 9.sp,
//                fontWeight = FontWeight.Bold
//            )
//
//            Text(
//                text = "${"%.6f".format(location.latitude)}, ${"%.6f".format(location.longitude)}",
//                color = Color(0xFF172033),
//                fontSize = 14.sp,
//                fontWeight = FontWeight.SemiBold
//            )
//        }
//
//        Spacer(modifier = Modifier.height(14.dp))
//
//        Button(
//            onClick = onRoute,
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(56.dp),
//            shape = RoundedCornerShape(16.dp),
//            colors = ButtonDefaults.buttonColors(
//                containerColor = Color(0xFF1565C0),
//                contentColor = Color.White
//            )
//        ) {
//            Text(
//                text = "🧭  ROUTE TO THIS LOCATION",
//                fontSize = 14.sp,
//                fontWeight = FontWeight.ExtraBold
//            )
//        }
//
//        Spacer(modifier = Modifier.height(10.dp))
//
//        Text(
//            text = "Your current location will be used as the route start.",
//            modifier = Modifier.fillMaxWidth(),
//            color = Color(0xFF98A2B3),
//            fontSize = 10.sp
//        )
//    }
//}
//
//
//// =============================================================
//// JOIN MESH SCREEN
//// =============================================================
//
//@Composable
//fun JoinMeshScreen(
//
//    devices: List<BluetoothDevice>,
//
//    onBack: () -> Unit,
//
//    onJoin: (BluetoothDevice) -> Unit
//
//) {
//
//    Column(
//
//        modifier =
//            Modifier
//                .fillMaxSize()
//                .padding(24.dp)
//
//    ) {
//
//        Text(
//
//            text =
//                "JOIN RESQMESH",
//
//            color =
//                Color(0xFF172033),
//
//            fontSize =
//                28.sp,
//
//            fontWeight =
//                FontWeight.Bold
//        )
//
//
//        Spacer(
//            modifier =
//                Modifier.height(8.dp)
//        )
//
//
//        Text(
//
//            text =
//                "Select a paired ResQMesh device.",
//
//            color =
//                Color(0xFF667085),
//
//            fontSize =
//                14.sp
//        )
//
//
//        Spacer(
//            modifier =
//                Modifier.height(20.dp)
//        )
//
//
//        if (devices.isEmpty()) {
//
//            Text(
//
//                text =
//                    "No paired Bluetooth devices found.",
//
//                color =
//                    Color(0xFF1565C0),
//
//                fontSize =
//                    15.sp
//            )
//
//            Spacer(
//                modifier =
//                    Modifier.height(8.dp)
//            )
//
//            Text(
//
//                text =
//                    "Pair both phones from Android Bluetooth Settings first.",
//
//                color =
//                    Color(0xFF667085),
//
//                fontSize =
//                    13.sp
//            )
//
//        } else {
//
//            devices.forEach { device ->
//
//                DeviceCard(
//
//                    device =
//                        device,
//
//                    onJoin = {
//
//                        onJoin(device)
//                    }
//                )
//
//                Spacer(
//                    modifier =
//                        Modifier.height(10.dp)
//                )
//            }
//        }
//
//
//        Spacer(
//            modifier =
//                Modifier.height(20.dp)
//        )
//
//
//        Button(
//
//            onClick =
//                onBack,
//
//            modifier =
//                Modifier.fillMaxWidth()
//
//        ) {
//
//            Text(
//                text =
//                    "← BACK"
//            )
//        }
//    }
//}
//
//
//// =============================================================
//// DEVICE CARD
//// =============================================================
//
//@Composable
//fun DeviceCard(
//
//    device: BluetoothDevice,
//
//    onJoin: () -> Unit
//
//) {
//
//    Column(
//
//        modifier =
//            Modifier
//                .fillMaxWidth()
//                .background(
//                    Color(0xFFFFFFFF),
//                    RoundedCornerShape(16.dp)
//                )
//                .border(
//                    1.dp,
//                    Color(0xFFD9E2EC),
//                    RoundedCornerShape(16.dp)
//                )
//                .padding(16.dp)
//
//    ) {
//
//        Text(
//
//            text =
//                safeDeviceName(device),
//
//            color =
//                Color(0xFF172033),
//
//            fontSize =
//                17.sp,
//
//            fontWeight =
//                FontWeight.Bold
//        )
//
//
//        Spacer(
//            modifier =
//                Modifier.height(5.dp)
//        )
//
//
//        Text(
//
//            text =
//                device.address,
//
//            color =
//                Color(0xFF667085),
//
//            fontSize =
//                12.sp
//        )
//
//
//        Spacer(
//            modifier =
//                Modifier.height(12.dp)
//        )
//
//
//        Button(
//
//            onClick =
//                onJoin,
//
//            modifier =
//                Modifier.fillMaxWidth(),
//
//            colors =
//                ButtonDefaults.buttonColors(
//
//                    containerColor =
//                        Color(0xFF1565C0),
//
//                    contentColor =
//                        Color(0xFFF7F9FC)
//                )
//
//        ) {
//
//            Text(
//
//                text =
//                    "CONNECT TO DEVICE",
//
//                fontWeight =
//                    FontWeight.Bold
//            )
//        }
//    }
//}
//
//
//// =============================================================
//// INFO CARD
//// =============================================================
//
//@Composable
//fun InfoCard(
//
//    value: String,
//
//    label: String,
//
//    modifier: Modifier
//
//) {
//
//    Column(
//
//        modifier =
//            modifier
//                .background(
//                    Color(0xFFFFFFFF),
//                    RoundedCornerShape(15.dp)
//                )
//                .border(
//                    1.dp,
//                    Color(0xFFD9E2EC),
//                    RoundedCornerShape(15.dp)
//                )
//                .padding(15.dp)
//
//    ) {
//
//        Text(
//
//            text =
//                value,
//
//            color =
//                Color(0xFF1565C0),
//
//            fontSize =
//                19.sp,
//
//            fontWeight =
//                FontWeight.Bold
//        )
//
//
//        Spacer(
//            modifier =
//                Modifier.height(3.dp)
//        )
//
//
//        Text(
//
//            text =
//                label,
//
//            color =
//                Color(0xFF667085),
//
//            fontSize =
//                10.sp
//        )
//    }
//}
//
//
//// =============================================================
//// SAFE DEVICE NAME
//// =============================================================
//
//fun safeDeviceName(
//    device: BluetoothDevice
//): String {
//
//    return try {
//
//        device.name
//            ?: "Unknown Device"
//
//    } catch (
//        e: SecurityException
//    ) {
//
//        "Unknown Device"
//    }
//}
//
//
//@Composable
//fun OfflineReportsScreen(
//    onBack: () -> Unit
//) {
//
//    val context = LocalContext.current
//
//    var reports by remember {
//        mutableStateOf<List<IncidentEntity>>(emptyList())
//    }
//
//    LaunchedEffect(Unit) {
//
//        val database =
//            ResQMeshDatabase.getDatabase(context)
//
//        reports =
//            database
//                .incidentDao()
//                .getAllIncidents()
//    }
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(Color(0xFFF7F9FC))
//    ) {
//
//        // =====================================================
//        // HEADER
//        // =====================================================
//
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .background(Color.White)
//                .padding(
//                    horizontal = 18.dp,
//                    vertical = 16.dp
//                ),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//
//            Text(
//                text = "‹",
//                color = Color(0xFF0D47A1),
//                fontSize = 32.sp,
//                fontWeight = FontWeight.Bold,
//                modifier = Modifier
//                    .clickable {
//                        onBack()
//                    }
//            )
//
//            Spacer(
//                modifier = Modifier.width(12.dp)
//            )
//
//            Column {
//
//                Text(
//                    text = "Offline Reports",
//                    color = Color(0xFF0D47A1),
//                    fontSize = 21.sp,
//                    fontWeight = FontWeight.ExtraBold
//                )
//
//                Text(
//                    text = "${reports.size} reports stored locally",
//                    color = Color(0xFF667085),
//                    fontSize = 11.sp
//                )
//            }
//        }
//
//        // =====================================================
//        // REPORT LIST
//        // =====================================================
//
//        if (reports.isEmpty()) {
//
//            Column(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(24.dp),
//                horizontalAlignment = Alignment.CenterHorizontally,
//                verticalArrangement = Arrangement.Center
//            ) {
//
//                Text(
//                    text = "📋",
//                    fontSize = 42.sp
//                )
//
//                Spacer(
//                    modifier = Modifier.height(12.dp)
//                )
//
//                Text(
//                    text = "No offline reports",
//                    color = Color(0xFF172033),
//                    fontSize = 17.sp,
//                    fontWeight = FontWeight.Bold
//                )
//
//                Spacer(
//                    modifier = Modifier.height(6.dp)
//                )
//
//                Text(
//                    text = "Reports created on this device will appear here.",
//                    color = Color(0xFF667085),
//                    fontSize = 12.sp
//                )
//            }
//
//        } else {
//
//            LazyColumn(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(
//                        horizontal = 16.dp,
//                        vertical = 14.dp
//                    ),
//                verticalArrangement =
//                    Arrangement.spacedBy(10.dp)
//            ) {
//
//                items(
//                    items = reports,
//                    key = { it.id }
//                ) { report ->
//
//                    OfflineReportCard(
//                        report = report
//                    )
//                }
//            }
//        }
//    }
//}
//
//
//@Composable
//fun OfflineReportCard(
//    report: IncidentEntity
//) {
//
//    val severityColor = when (report.severity.uppercase()) {
//        "CRITICAL" -> Color(0xFFD32F2F)
//        "HIGH" -> Color(0xFFE65100)
//        "MEDIUM" -> Color(0xFFF9A825)
//        else -> Color(0xFF16A34A)
//    }
//
//    val icon = when (report.type.uppercase()) {
//        "FIRE" -> "🔥"
//        "FLOOD" -> "🌊"
//        "ROAD_BLOCKED" -> "🚧"
//        "ACCIDENT" -> "🚑"
//        "BUILDING_COLLAPSE" -> "🏚️"
//        "MEDICAL_EMERGENCY" -> "🏥"
//        "DANGEROUS_AREA" -> "⚠️"
//        "MISSING_PERSON" -> "🔎"
//        "PEOPLE_TRAPPED" -> "🆘"
//        "NATURAL_DISASTER" -> "🌪️"
//        else -> "📍"
//    }
//
//    Column(
//        modifier = Modifier
//            .fillMaxWidth()
//            .background(
//                Color.White,
//                RoundedCornerShape(16.dp)
//            )
//            .border(
//                1.dp,
//                Color(0xFFD9E2EC),
//                RoundedCornerShape(16.dp)
//            )
//            .padding(14.dp)
//    ) {
//
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//
//            Text(
//                text = icon,
//                fontSize = 27.sp
//            )
//
//            Spacer(
//                modifier = Modifier.width(12.dp)
//            )
//
//            Column(
//                modifier = Modifier.weight(1f)
//            ) {
//
//                Text(
//                    text = report.type.replace("_", " "),
//                    color = Color(0xFF172033),
//                    fontSize = 15.sp,
//                    fontWeight = FontWeight.Bold
//                )
//
//                Spacer(
//                    modifier = Modifier.height(3.dp)
//                )
//
//                Text(
//                    text = report.description,
//                    color = Color(0xFF667085),
//                    fontSize = 11.sp,
//                    maxLines = 2
//                )
//            }
//
//            Text(
//                text = report.severity,
//                color = severityColor,
//                fontSize = 9.sp,
//                fontWeight = FontWeight.ExtraBold
//            )
//        }
//
//        Spacer(
//            modifier = Modifier.height(10.dp)
//        )
//
//        HorizontalDivider(
//            color = Color(0xFFE4E7EC)
//        )
//
//        Spacer(
//            modifier = Modifier.height(8.dp)
//        )
//
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.SpaceBetween
//        ) {
//
//            Text(
//                text = if (report.verified)
//                    "✓ VERIFIED"
//                else
//                    "○ UNVERIFIED",
//                color = if (report.verified)
//                    Color(0xFF16A34A)
//                else
//                    Color(0xFF667085),
//                fontSize = 9.sp,
//                fontWeight = FontWeight.Bold
//            )
//
//            Text(
//                text = if (
//                    report.latitude != null &&
//                    report.longitude != null
//                ) {
//                    "📍 LOCATION AVAILABLE"
//                } else {
//                    "📍 NO LOCATION"
//                },
//                color = Color(0xFF667085),
//                fontSize = 9.sp,
//                fontWeight = FontWeight.Bold
//            )
//        }
//    }
//}
//
//
//
////
////
////
////
////package com.resqmesh.app
////
////// mport androidx.compose.foundation.layout.weight
////
////import android.Manifest
////import android.bluetooth.BluetoothDevice
////import android.content.pm.PackageManager
////import android.os.Build
////import android.os.Bundle
////import android.util.Log
////import androidx.activity.ComponentActivity
////import androidx.activity.compose.rememberLauncherForActivityResult
////import androidx.activity.compose.setContent
////import androidx.activity.result.contract.ActivityResultContracts
////import androidx.compose.foundation.background
////import androidx.compose.foundation.border
////import androidx.compose.foundation.layout.Arrangement
////import androidx.compose.foundation.layout.Column
////import androidx.compose.foundation.layout.Row
////import androidx.compose.foundation.layout.Spacer
////import androidx.compose.foundation.layout.fillMaxSize
////import androidx.compose.foundation.layout.fillMaxWidth
////import androidx.compose.foundation.layout.height
////import androidx.compose.foundation.layout.padding
////import androidx.compose.foundation.lazy.LazyColumn
////import androidx.compose.foundation.lazy.items
////import androidx.compose.foundation.shape.RoundedCornerShape
////import androidx.compose.material3.Button
////import androidx.compose.material3.ButtonDefaults
////import androidx.compose.material3.OutlinedTextField
////import androidx.compose.material3.Surface
////import androidx.compose.material3.Text
////import androidx.compose.runtime.Composable
////import androidx.compose.runtime.LaunchedEffect
////import androidx.compose.runtime.getValue
////import androidx.compose.runtime.mutableStateListOf
////import androidx.compose.runtime.mutableStateOf
////import androidx.compose.runtime.remember
////import androidx.compose.runtime.setValue
////import androidx.compose.ui.Alignment
////import androidx.compose.ui.Modifier
////import androidx.compose.ui.graphics.Color
////import androidx.compose.ui.platform.LocalContext
////import androidx.compose.ui.text.font.FontWeight
////import androidx.compose.ui.unit.dp
////import androidx.compose.ui.unit.sp
////import androidx.core.content.ContextCompat
////import com.resqmesh.app.database.ResQMeshDatabase
////import com.resqmesh.app.model.EmergencyPacketEntity
////import com.resqmesh.app.network.NearbyMeshManager
////import com.resqmesh.app.ui.EmergencyScreen
////import com.resqmesh.app.ui.IncomingEmergencyScreen
////import com.resqmesh.app.ui.theme.MapScreen
////import com.resqmesh.app.ui.ReportIncidentScreen
////import com.resqmesh.app.ui.theme.ResQMeshTheme
////
////
////// =============================================================
////// MAIN ACTIVITY
////// =============================================================
////
////class MainActivity : ComponentActivity() {
////
////    override fun onCreate(
////        savedInstanceState: Bundle?
////    ) {
////
////        super.onCreate(savedInstanceState)
////
////        setContent {
////
////            ResQMeshTheme {
////
////                ResQMeshApp()
////            }
////        }
////    }
////}
////
////
////// =============================================================
////// APP
////// =============================================================
////
////@Composable
////fun ResQMeshApp() {
////
////    val context =
////        LocalContext.current
////
////    val nearbyMeshManager =
////        remember {
////            NearbyMeshManager(context)
////        }
////
////    var nearbyDeviceCount by remember {
////        mutableStateOf(0)
////    }
////
////    var meshActive by remember {
////        mutableStateOf(false)
////    }
////
////    var incomingEmergencyPacket by remember {
////        mutableStateOf<EmergencyPacketEntity?>(null)
////    }
////
////    var showReportScreen by remember {
////        mutableStateOf(false)
////    }
////
////    var showEmergencyScreen by remember {
////        mutableStateOf(false)
////    }
////
////    var showJoinScreen by remember {
////        mutableStateOf(false)
////    }
////
////    var showMapScreen by remember { mutableStateOf(false) }
////
////    var showMessagingScreen by remember {
////        mutableStateOf(false)
////    }
////
////    var pairedDevices by remember {
////        mutableStateOf<List<BluetoothDevice>>(emptyList())
////    }
////
////    /*
////     * =========================================================
////     * MESH MESSAGE STATE
////     * =========================================================
////     */
////
////    val receivedMessages =
////        remember {
////            mutableStateListOf<String>()
////        }
////
////
////    // =========================================================
////    // CALLBACKS
////    // =========================================================
////
////    LaunchedEffect(Unit) {
////
////        nearbyMeshManager.onDeviceCountChanged =
////            { count ->
////
////                nearbyDeviceCount =
////                    count
////
////                Log.d(
////                    "ResQMeshBluetooth",
////                    "CONNECTED RESQMESH NODES = $count"
////                )
////            }
////
////
////        nearbyMeshManager.onEmergencyPacketReceived =
////            { packet ->
////
////                Log.d(
////                    "ResQMeshBluetooth",
////                    "INCOMING SOS = ${packet.packetId}"
////                )
////
////                incomingEmergencyPacket =
////                    packet
////            }
////
////
////        /*
////         * Normal mesh message callback.
////         */
////        nearbyMeshManager.onMessageReceived =
////            { message ->
////
////                Log.d(
////                    "ResQMeshBluetooth",
////                    "INCOMING MESH MESSAGE = $message"
////                )
////
////                receivedMessages.add(
////                    message
////                )
////            }
////    }
////
////
////    // =========================================================
////    // PERMISSIONS
////    // =========================================================
////
////    val nearbyPermissions =
////        if (
////            Build.VERSION.SDK_INT >=
////            Build.VERSION_CODES.TIRAMISU
////        ) {
////
////            arrayOf(
////                Manifest.permission.BLUETOOTH_SCAN,
////                Manifest.permission.BLUETOOTH_CONNECT,
////                Manifest.permission.BLUETOOTH_ADVERTISE,
////                Manifest.permission.NEARBY_WIFI_DEVICES,
////                Manifest.permission.ACCESS_FINE_LOCATION,
////                Manifest.permission.ACCESS_COARSE_LOCATION
////            )
////
////        } else if (
////            Build.VERSION.SDK_INT >=
////            Build.VERSION_CODES.S
////        ) {
////
////            arrayOf(
////                Manifest.permission.BLUETOOTH_SCAN,
////                Manifest.permission.BLUETOOTH_CONNECT,
////                Manifest.permission.BLUETOOTH_ADVERTISE,
////                Manifest.permission.ACCESS_FINE_LOCATION,
////                Manifest.permission.ACCESS_COARSE_LOCATION
////            )
////
////        } else {
////
////            arrayOf(
////                Manifest.permission.ACCESS_FINE_LOCATION,
////                Manifest.permission.ACCESS_COARSE_LOCATION
////            )
////        }
////
////
////    // =========================================================
////    // CREATE MESH PERMISSION
////    // =========================================================
////
////    val createMeshLauncher =
////        rememberLauncherForActivityResult(
////            ActivityResultContracts.RequestMultiplePermissions()
////        ) { permissions ->
////
////            val allGranted =
////                nearbyPermissions.all { permission ->
////
////                    permissions[permission] == true ||
////                            ContextCompat.checkSelfPermission(
////                                context,
////                                permission
////                            ) ==
////                            PackageManager.PERMISSION_GRANTED
////                }
////
////
////            if (allGranted) {
////
////                Log.d(
////                    "ResQMeshBluetooth",
////                    "ALL BLUETOOTH PERMISSIONS GRANTED"
////                )
////
////                meshActive =
////                    true
////
////                nearbyMeshManager.startMesh()
////
////            } else {
////
////                Log.e(
////                    "ResQMeshBluetooth",
////                    "BLUETOOTH PERMISSIONS DENIED"
////                )
////            }
////        }
////
////
////    // =========================================================
////    // JOIN MESH PERMISSION
////    // =========================================================
////
////    val joinMeshLauncher =
////        rememberLauncherForActivityResult(
////            ActivityResultContracts.RequestMultiplePermissions()
////        ) { permissions ->
////
////            val allGranted =
////                nearbyPermissions.all { permission ->
////
////                    permissions[permission] == true ||
////                            ContextCompat.checkSelfPermission(
////                                context,
////                                permission
////                            ) ==
////                            PackageManager.PERMISSION_GRANTED
////                }
////
////
////            if (allGranted) {
////
////                Log.d(
////                    "ResQMeshBluetooth",
////                    "ALL BLUETOOTH PERMISSIONS GRANTED"
////                )
////
////                pairedDevices =
////                    nearbyMeshManager.getPairedDevices()
////
////                Log.d(
////                    "ResQMeshBluetooth",
////                    "PAIRED DEVICES = ${pairedDevices.size}"
////                )
////
////                showJoinScreen =
////                    true
////
////            } else {
////
////                Log.e(
////                    "ResQMeshBluetooth",
////                    "BLUETOOTH PERMISSIONS DENIED"
////                )
////            }
////        }
////
////
////    // =========================================================
////    // MAIN ROUTING
////    // =========================================================
////
////    Surface(
////        modifier =
////            Modifier.fillMaxSize(),
////
////        color =
////            Color(0xFFF7F9FC)
////    ) {
////
////        when {
////
////            // -------------------------------------------------
////            // INCOMING SOS
////            // -------------------------------------------------
////
////            incomingEmergencyPacket != null -> {
////
////                IncomingEmergencyScreen(
////
////                    packet =
////                        incomingEmergencyPacket!!,
////
////                    onAcknowledge = {
////
////                        incomingEmergencyPacket =
////                            null
////                    }
////                )
////            }
////
////
////            // -------------------------------------------------
////            // EMERGENCY
////            // -------------------------------------------------
////
////            showEmergencyScreen -> {
////
////                EmergencyScreen(
////
////                    onCancel = {
////
////                        showEmergencyScreen =
////                            false
////                    }
////                )
////            }
////
////
////            // -------------------------------------------------
////            // REPORT
////            // -------------------------------------------------
////
////            showReportScreen -> {
////
////                ReportIncidentScreen()
////            }
////
////            showMapScreen -> {
////                MapScreen()
////            }
////
////
////            // -------------------------------------------------
////            // JOIN
////            // -------------------------------------------------
////
////            showJoinScreen -> {
////
////                JoinMeshScreen(
////
////                    devices =
////                        pairedDevices,
////
////                    onBack = {
////
////                        showJoinScreen =
////                            false
////                    },
////
////                    onJoin = { device ->
////
////                        Log.d(
////                            "ResQMeshBluetooth",
////                            "JOINING SELECTED DEVICE"
////                        )
////
////                        meshActive =
////                            true
////
////                        showJoinScreen =
////                            false
////
////                        nearbyMeshManager.joinMesh(
////                            device
////                        )
////                    }
////                )
////            }
////
////
////            // -------------------------------------------------
////            // MESSAGING
////            // -------------------------------------------------
////
////            showMessagingScreen -> {
////
////                MeshMessagingScreen(
////
////                    connectedNodes =
////                        nearbyDeviceCount,
////
////                    messages =
////                        receivedMessages,
////
////                    onSendMessage = { message ->
////
////                        nearbyMeshManager
////                            .sendTextMessage(
////                                message
////                            )
////                    },
////
////                    onBack = {
////
////                        showMessagingScreen =
////                            false
////                    }
////                )
////            }
////
////
////            // -------------------------------------------------
////            // HOME
////            // -------------------------------------------------
////
////            else -> {
////
////                HomeScreen(
////
////                    onCreateMesh = {
////
////                        Log.d(
////                            "ResQMeshBluetooth",
////                            "REQUESTING HOST PERMISSIONS"
////                        )
////
////                        createMeshLauncher.launch(
////                            nearbyPermissions
////                        )
////                    },
////
////                    onJoinMesh = {
////
////                        Log.d(
////                            "ResQMeshBluetooth",
////                            "REQUESTING JOIN PERMISSIONS"
////                        )
////
////                        joinMeshLauncher.launch(
////                            nearbyPermissions
////                        )
////                    },
////
////                    onReportIncident = {
////
////                        showReportScreen =
////                            true
////                    },
////
////                    onEmergency = {
////
////                        showEmergencyScreen =
////                            true
////                    },
////
////                    onMap = {
////                        showMapScreen = true
////                    },
////
////                    onMessaging = {
////
////                        showMessagingScreen =
////                            true
////                    },
////
////                    nearbyDeviceCount =
////                        nearbyDeviceCount,
////
////                    meshActive =
////                        meshActive
////                )
////            }
////        }
////    }
////}
////
////
////// =============================================================
////// HOME SCREEN
////// =============================================================
////
////@Composable
////fun HomeScreen(
////    onMap: () -> Unit,
////    onCreateMesh: () -> Unit,
////    onJoinMesh: () -> Unit,
////    onReportIncident: () -> Unit,
////    onEmergency: () -> Unit,
////    onMessaging: () -> Unit,
////    nearbyDeviceCount: Int,
////    meshActive: Boolean
////) {
////
////    val context = LocalContext.current
////
////    var incidentCount by remember {
////        mutableStateOf(0)
////    }
////
////    LaunchedEffect(Unit) {
////
////        val database =
////            ResQMeshDatabase.getDatabase(context)
////
////        incidentCount =
////            database
////                .incidentDao()
////                .getIncidentCount()
////    }
////
////    Column(
////        modifier = Modifier
////            .fillMaxSize()
////            .background(Color(0xFFF7F9FC))
////            .padding(
////                horizontal = 18.dp,
////                vertical = 14.dp
////            ),
////        horizontalAlignment = Alignment.CenterHorizontally
////    ) {
////
////        // =====================================================
////        // HEADER
////        // =====================================================
////
////        Row(
////            modifier = Modifier.fillMaxWidth(),
////            verticalAlignment = Alignment.CenterVertically
////        ) {
////
////            Column(
////                modifier = Modifier.weight(1f)
////            ) {
////
////                Text(
////                    text = "ResQMesh",
////                    color = Color(0xFF0D47A1),
////                    fontSize = 28.sp,
////                    fontWeight = FontWeight.ExtraBold
////                )
////
////                Text(
////                    text = "Emergency Response Network",
////                    color = Color(0xFF667085),
////                    fontSize = 12.sp
////                )
////            }
////
////            Column(
////                horizontalAlignment = Alignment.End
////            ) {
////
////                Text(
////                    text = if (meshActive) "● ONLINE" else "● STANDBY",
////                    color = if (meshActive)
////                        Color(0xFF16A34A)
////                    else
////                        Color(0xFF667085),
////                    fontSize = 11.sp,
////                    fontWeight = FontWeight.Bold
////                )
////
////                Text(
////                    text = "$nearbyDeviceCount NODE" +
////                            if (nearbyDeviceCount == 1) "" else "S",
////                    color = Color(0xFF667085),
////                    fontSize = 10.sp
////                )
////            }
////        }
////
////        Spacer(
////            modifier = Modifier.height(12.dp)
////        )
////
////        // =====================================================
////        // NETWORK STATUS CARD
////        // =====================================================
////
////        Row(
////            modifier = Modifier
////                .fillMaxWidth()
////                .background(
////                    Color.White,
////                    RoundedCornerShape(16.dp)
////                )
////                .border(
////                    1.dp,
////                    Color(0xFFD9E2EC),
////                    RoundedCornerShape(16.dp)
////                )
////                .padding(
////                    horizontal = 15.dp,
////                    vertical = 12.dp
////                ),
////            verticalAlignment = Alignment.CenterVertically
////        ) {
////
////            Column(
////                modifier = Modifier.weight(1f)
////            ) {
////
////                Text(
////                    text = "NETWORK STATUS",
////                    color = Color(0xFF667085),
////                    fontSize = 9.sp,
////                    fontWeight = FontWeight.Bold
////                )
////
////                Spacer(
////                    modifier = Modifier.height(2.dp)
////                )
////
////                Text(
////                    text = if (meshActive)
////                        "Mesh network active"
////                    else
////                        "Ready to establish network",
////                    color = Color(0xFF172033),
////                    fontSize = 13.sp,
////                    fontWeight = FontWeight.SemiBold
////                )
////            }
////
////            Text(
////                text = "$nearbyDeviceCount\nNODES",
////                color = Color(0xFF1565C0),
////                fontSize = 12.sp,
////                fontWeight = FontWeight.Bold
////            )
////        }
////
////        Spacer(
////            modifier = Modifier.height(14.dp)
////        )
////
////        // =====================================================
////        // MAIN ACTION GRID
////        // =====================================================
////
////        Row(
////            modifier = Modifier.fillMaxWidth(),
////            horizontalArrangement = Arrangement.spacedBy(10.dp)
////        ) {
////
////            DashboardAction(
////                modifier = Modifier.weight(1f),
////                icon = "📡",
////                title = "CREATE MESH",
////                subtitle = "Start network",
////                onClick = onCreateMesh
////            )
////
////            DashboardAction(
////                modifier = Modifier.weight(1f),
////                icon = "🔗",
////                title = "JOIN MESH",
////                subtitle = "Connect node",
////                onClick = onJoinMesh
////            )
////        }
////
////        Spacer(
////            modifier = Modifier.height(10.dp)
////        )
////
////        Row(
////            modifier = Modifier.fillMaxWidth(),
////            horizontalArrangement = Arrangement.spacedBy(10.dp)
////        ) {
////
////            DashboardAction(
////                modifier = Modifier.weight(1f),
////                icon = "💬",
////                title = "MESSAGING",
////                subtitle = "Mesh messages",
////                onClick = onMessaging
////            )
////
////            DashboardAction(
////                modifier = Modifier.weight(1f),
////                icon = "🗺️",
////                title = "MAP",
////                subtitle = "Live location",
////                onClick = onMap
////            )
////        }
////
////        Spacer(
////            modifier = Modifier.height(12.dp)
////        )
////
////        // =====================================================
////        // SOS
////        // =====================================================
////
////        Button(
////            onClick = onEmergency,
////            modifier = Modifier
////                .fillMaxWidth()
////                .height(58.dp),
////            shape = RoundedCornerShape(16.dp),
////            colors = ButtonDefaults.buttonColors(
////                containerColor = Color(0xFFD32F2F),
////                contentColor = Color.White
////            )
////        ) {
////
////            Column(
////                horizontalAlignment = Alignment.CenterHorizontally
////            ) {
////
////                Text(
////                    text = "🚨  I'M IN DANGER",
////                    fontSize = 15.sp,
////                    fontWeight = FontWeight.ExtraBold
////                )
////
////                Text(
////                    text = "Send emergency alert",
////                    fontSize = 10.sp
////                )
////            }
////        }
////
////        Spacer(
////            modifier = Modifier.height(10.dp)
////        )
////
////        // =====================================================
////        // REPORT INCIDENT
////        // =====================================================
////
////        Button(
////            onClick = onReportIncident,
////            modifier = Modifier
////                .fillMaxWidth()
////                .height(50.dp),
////            shape = RoundedCornerShape(14.dp),
////            colors = ButtonDefaults.buttonColors(
////                containerColor = Color.White,
////                contentColor = Color(0xFF172033)
////            )
////        ) {
////
////            Text(
////                text = "🚧  REPORT INCIDENT",
////                fontWeight = FontWeight.Bold,
////                fontSize = 13.sp
////            )
////        }
////
////        Spacer(
////            modifier = Modifier.height(10.dp)
////        )
////
////        // =====================================================
////        // QUICK STATS
////        // =====================================================
////
////        Row(
////            modifier = Modifier.fillMaxWidth(),
////            horizontalArrangement = Arrangement.spacedBy(10.dp)
////        ) {
////
////            CompactInfoCard(
////                modifier = Modifier.weight(1f),
////                value = incidentCount.toString(),
////                label = "OFFLINE REPORTS"
////            )
////
////            CompactInfoCard(
////                modifier = Modifier.weight(1f),
////                value = "READY",
////                label = "LOCAL STORAGE"
////            )
////        }
////
////        Spacer(
////            modifier = Modifier.height(8.dp)
////        )
////
////        Text(
////            text = "LOW-BANDWIDTH • RESILIENT • EMERGENCY READY",
////            color = Color(0xFF98A2B3),
////            fontSize = 8.sp,
////            fontWeight = FontWeight.Bold
////        )
////    }
////}
////
////@Composable
////fun DashboardAction(
////    modifier: Modifier = Modifier,
////    icon: String,
////    title: String,
////    subtitle: String,
////    onClick: () -> Unit
////) {
////
////    Button(
////        onClick = onClick,
////        modifier = modifier.height(78.dp),
////        shape = RoundedCornerShape(16.dp),
////        colors = ButtonDefaults.buttonColors(
////            containerColor = Color.White,
////            contentColor = Color(0xFF172033)
////        )
////    ) {
////
////        Column(
////            horizontalAlignment = Alignment.CenterHorizontally
////        ) {
////
////            Text(
////                text = icon,
////                fontSize = 20.sp
////            )
////
////            Text(
////                text = title,
////                fontSize = 11.sp,
////                fontWeight = FontWeight.ExtraBold
////            )
////
////            Text(
////                text = subtitle,
////                fontSize = 8.sp,
////                color = Color(0xFF667085)
////            )
////        }
////    }
////}
////
////
////@Composable
////fun CompactInfoCard(
////    modifier: Modifier = Modifier,
////    value: String,
////    label: String
////) {
////
////    Column(
////        modifier = modifier
////            .background(
////                Color.White,
////                RoundedCornerShape(14.dp)
////            )
////            .border(
////                1.dp,
////                Color(0xFFD9E2EC),
////                RoundedCornerShape(14.dp)
////            )
////            .padding(
////                horizontal = 12.dp,
////                vertical = 9.dp
////            ),
////        horizontalAlignment = Alignment.CenterHorizontally
////    ) {
////
////        Text(
////            text = value,
////            color = Color(0xFF1565C0),
////            fontSize = 16.sp,
////            fontWeight = FontWeight.ExtraBold
////        )
////
////        Text(
////            text = label,
////            color = Color(0xFF667085),
////            fontSize = 8.sp,
////            fontWeight = FontWeight.Bold
////        )
////    }
////}
////@Composable
////fun MeshMessagingScreen(
////
////    connectedNodes: Int,
////
////    messages: List<String>,
////
////    onSendMessage: (String) -> Unit,
////
////    onBack: () -> Unit
////
////) {
////
////    var messageText by remember {
////        mutableStateOf("")
////    }
////
////
////    Column(
////
////        modifier =
////            Modifier
////                .fillMaxSize()
////                .padding(20.dp)
////
////    ) {
////
////        // =====================================================
////        // HEADER
////        // =====================================================
////
////        Row(
////
////            modifier =
////                Modifier.fillMaxWidth(),
////
////            verticalAlignment =
////                Alignment.CenterVertically
////
////        ) {
////
////            Button(
////                onClick =
////                    onBack,
////
////                colors =
////                    ButtonDefaults.buttonColors(
////
////                        containerColor =
////                            Color(0xFFFFFFFF),
////
////                        contentColor =
////                            Color(0xFF172033)
////                    )
////            ) {
////
////                Text(
////                    text =
////                        "←"
////                )
////            }
////
////
////            Spacer(
////                modifier =
////                    Modifier.weight(1f)
////            )
////
////
////            Column(
////                horizontalAlignment =
////                    Alignment.End
////            ) {
////
////                Text(
////
////                    text =
////                        "MESH MESSAGING",
////
////                    color =
////                        Color(0xFF172033),
////
////                    fontSize =
////                        21.sp,
////
////                    fontWeight =
////                        FontWeight.Bold
////                )
////
////                Text(
////
////                    text =
////                        "$connectedNodes connected node" +
////                                if (
////                                    connectedNodes == 1
////                                ) {
////                                    ""
////                                } else {
////                                    "s"
////                                },
////
////                    color =
////                        if (
////                            connectedNodes > 0
////                        ) {
////
////                            Color(0xFF16A34A)
////
////                        } else {
////
////                            Color(0xFF1565C0)
////                        },
////
////                    fontSize =
////                        12.sp
////                )
////            }
////        }
////
////
////        Spacer(
////            modifier =
////                Modifier.height(18.dp)
////        )
////
////
////        // =====================================================
////        // INFORMATION
////        // =====================================================
////
////        Column(
////
////            modifier =
////                Modifier
////                    .fillMaxWidth()
////                    .background(
////                        Color(0xFFFFFFFF),
////                        RoundedCornerShape(15.dp)
////                    )
////                    .border(
////                        1.dp,
////                        Color(0xFFD9E2EC),
////                        RoundedCornerShape(15.dp)
////                    )
////                    .padding(15.dp)
////
////        ) {
////
////            Text(
////
////                text =
////                    "📡 LOW-BANDWIDTH MESH",
////
////                color =
////                    Color(0xFF1565C0),
////
////                fontWeight =
////                    FontWeight.Bold,
////
////                fontSize =
////                    13.sp
////            )
////
////            Spacer(
////                modifier =
////                    Modifier.height(5.dp)
////            )
////
////            Text(
////
////                text =
////                    "Messages are stored locally and can " +
////                            "travel through connected ResQMesh nodes.",
////
////                color =
////                    Color(0xFF667085),
////
////                fontSize =
////                    12.sp
////            )
////        }
////
////
////        Spacer(
////            modifier =
////                Modifier.height(15.dp)
////        )
////
////
////        // =====================================================
////        // MESSAGES
////        // =====================================================
////
////        Text(
////
////            text =
////                "MESSAGES",
////
////            color =
////                Color(0xFF667085),
////
////            fontSize =
////                11.sp,
////
////            fontWeight =
////                FontWeight.Bold
////        )
////
////
////        Spacer(
////            modifier =
////                Modifier.height(8.dp)
////        )
////
////
////        LazyColumn(
////
////            modifier =
////                Modifier
////                    .weight(1f)
////                    .fillMaxWidth(),
////
////            verticalArrangement =
////                Arrangement.spacedBy(8.dp)
////
////        ) {
////
////            if (messages.isEmpty()) {
////
////                item {
////
////                    Column(
////
////                        modifier =
////                            Modifier
////                                .fillMaxWidth()
////                                .padding(
////                                    top = 40.dp
////                                ),
////
////                        horizontalAlignment =
////                            Alignment.CenterHorizontally
////
////                    ) {
////
////                        Text(
////
////                            text =
////                                "No messages yet",
////
////                            color =
////                                Color(0xFF667085),
////
////                            fontSize =
////                                14.sp
////                        )
////
////                        Spacer(
////                            modifier =
////                                Modifier.height(5.dp)
////                        )
////
////                        Text(
////
////                            text =
////                                "Send a message when another node is connected.",
////
////                            color =
////                                Color(0xFF98A2B3),
////
////                            fontSize =
////                                11.sp
////                        )
////                    }
////                }
////
////            } else {
////
////                items(messages) { message ->
////
////                    MessageBubble(
////                        message =
////                            message
////                    )
////                }
////            }
////        }
////
////
////        Spacer(
////            modifier =
////                Modifier.height(10.dp)
////        )
////
////
////        // =====================================================
////        // MESSAGE INPUT
////        // =====================================================
////
////        OutlinedTextField(
////
////            value =
////                messageText,
////
////            onValueChange = {
////                messageText = it
////            },
////
////            modifier =
////                Modifier.fillMaxWidth(),
////
////            placeholder = {
////
////                Text(
////                    text =
////                        "Type emergency message..."
////                )
////            },
////
////            singleLine = false,
////
////            maxLines = 4,
////
////            shape =
////                RoundedCornerShape(15.dp)
////        )
////
////
////        Spacer(
////            modifier =
////                Modifier.height(8.dp)
////        )
////
////
////        Button(
////
////            onClick = {
////
////                if (
////                    messageText.isNotBlank()
////                ) {
////
////                    onSendMessage(
////                        messageText.trim()
////                    )
////
////                    messageText =
////                        ""
////                }
////            },
////
////            modifier =
////                Modifier
////                    .fillMaxWidth()
////                    .height(52.dp),
////
////            enabled =
////                messageText.isNotBlank(),
////
////            shape =
////                RoundedCornerShape(15.dp),
////
////            colors =
////                ButtonDefaults.buttonColors(
////
////                    containerColor =
////                        Color(0xFF1565C0),
////
////                    contentColor =
////                        Color(0xFFF7F9FC)
////                )
////
////        ) {
////
////            Text(
////
////                text =
////                    "📨 SEND THROUGH MESH",
////
////                fontWeight =
////                    FontWeight.Bold
////            )
////        }
////    }
////}
////
////
////// =============================================================
////// MESSAGE BUBBLE
////// =============================================================
////
////@Composable
////fun MessageBubble(
////    message: String
////) {
////
////    Column(
////
////        modifier =
////            Modifier
////                .fillMaxWidth()
////                .background(
////                    Color(0xFFFFFFFF),
////                    RoundedCornerShape(14.dp)
////                )
////                .border(
////                    1.dp,
////                    Color(0xFFD9E2EC),
////                    RoundedCornerShape(14.dp)
////                )
////                .padding(13.dp)
////
////    ) {
////
////        Text(
////
////            text =
////                "📨 INCOMING MESH MESSAGE",
////
////            color =
////                Color(0xFF1565C0),
////
////            fontSize =
////                10.sp,
////
////            fontWeight =
////                FontWeight.Bold
////        )
////
////        Spacer(
////            modifier =
////                Modifier.height(5.dp)
////        )
////
////        Text(
////
////            text =
////                message,
////
////            color =
////                Color(0xFF172033),
////
////            fontSize =
////                14.sp
////        )
////    }
////}
////
////
////// =============================================================
////// JOIN MESH SCREEN
////// =============================================================
////
////@Composable
////fun JoinMeshScreen(
////
////    devices: List<BluetoothDevice>,
////
////    onBack: () -> Unit,
////
////    onJoin: (BluetoothDevice) -> Unit
////
////) {
////
////    Column(
////
////        modifier =
////            Modifier
////                .fillMaxSize()
////                .padding(24.dp)
////
////    ) {
////
////        Text(
////
////            text =
////                "JOIN RESQMESH",
////
////            color =
////                Color(0xFF172033),
////
////            fontSize =
////                28.sp,
////
////            fontWeight =
////                FontWeight.Bold
////        )
////
////
////        Spacer(
////            modifier =
////                Modifier.height(8.dp)
////        )
////
////
////        Text(
////
////            text =
////                "Select a paired ResQMesh device.",
////
////            color =
////                Color(0xFF667085),
////
////            fontSize =
////                14.sp
////        )
////
////
////        Spacer(
////            modifier =
////                Modifier.height(20.dp)
////        )
////
////
////        if (devices.isEmpty()) {
////
////            Text(
////
////                text =
////                    "No paired Bluetooth devices found.",
////
////                color =
////                    Color(0xFF1565C0),
////
////                fontSize =
////                    15.sp
////            )
////
////            Spacer(
////                modifier =
////                    Modifier.height(8.dp)
////            )
////
////            Text(
////
////                text =
////                    "Pair both phones from Android Bluetooth Settings first.",
////
////                color =
////                    Color(0xFF667085),
////
////                fontSize =
////                    13.sp
////            )
////
////        } else {
////
////            devices.forEach { device ->
////
////                DeviceCard(
////
////                    device =
////                        device,
////
////                    onJoin = {
////
////                        onJoin(device)
////                    }
////                )
////
////                Spacer(
////                    modifier =
////                        Modifier.height(10.dp)
////                )
////            }
////        }
////
////
////        Spacer(
////            modifier =
////                Modifier.height(20.dp)
////        )
////
////
////        Button(
////
////            onClick =
////                onBack,
////
////            modifier =
////                Modifier.fillMaxWidth()
////
////        ) {
////
////            Text(
////                text =
////                    "← BACK"
////            )
////        }
////    }
////}
////
////
////// =============================================================
////// DEVICE CARD
////// =============================================================
////
////@Composable
////fun DeviceCard(
////
////    device: BluetoothDevice,
////
////    onJoin: () -> Unit
////
////) {
////
////    Column(
////
////        modifier =
////            Modifier
////                .fillMaxWidth()
////                .background(
////                    Color(0xFFFFFFFF),
////                    RoundedCornerShape(16.dp)
////                )
////                .border(
////                    1.dp,
////                    Color(0xFFD9E2EC),
////                    RoundedCornerShape(16.dp)
////                )
////                .padding(16.dp)
////
////    ) {
////
////        Text(
////
////            text =
////                safeDeviceName(device),
////
////            color =
////                Color(0xFF172033),
////
////            fontSize =
////                17.sp,
////
////            fontWeight =
////                FontWeight.Bold
////        )
////
////
////        Spacer(
////            modifier =
////                Modifier.height(5.dp)
////        )
////
////
////        Text(
////
////            text =
////                device.address,
////
////            color =
////                Color(0xFF667085),
////
////            fontSize =
////                12.sp
////        )
////
////
////        Spacer(
////            modifier =
////                Modifier.height(12.dp)
////        )
////
////
////        Button(
////
////            onClick =
////                onJoin,
////
////            modifier =
////                Modifier.fillMaxWidth(),
////
////            colors =
////                ButtonDefaults.buttonColors(
////
////                    containerColor =
////                        Color(0xFF1565C0),
////
////                    contentColor =
////                        Color(0xFFF7F9FC)
////                )
////
////        ) {
////
////            Text(
////
////                text =
////                    "CONNECT TO DEVICE",
////
////                fontWeight =
////                    FontWeight.Bold
////            )
////        }
////    }
////}
////
////
////// =============================================================
////// INFO CARD
////// =============================================================
////
////@Composable
////fun InfoCard(
////
////    value: String,
////
////    label: String,
////
////    modifier: Modifier
////
////) {
////
////    Column(
////
////        modifier =
////            modifier
////                .background(
////                    Color(0xFFFFFFFF),
////                    RoundedCornerShape(15.dp)
////                )
////                .border(
////                    1.dp,
////                    Color(0xFFD9E2EC),
////                    RoundedCornerShape(15.dp)
////                )
////                .padding(15.dp)
////
////    ) {
////
////        Text(
////
////            text =
////                value,
////
////            color =
////                Color(0xFF1565C0),
////
////            fontSize =
////                19.sp,
////
////            fontWeight =
////                FontWeight.Bold
////        )
////
////
////        Spacer(
////            modifier =
////                Modifier.height(3.dp)
////        )
////
////
////        Text(
////
////            text =
////                label,
////
////            color =
////                Color(0xFF667085),
////
////            fontSize =
////                10.sp
////        )
////    }
////}
////
////
////// =============================================================
////// SAFE DEVICE NAME
////// =============================================================
////
////fun safeDeviceName(
////    device: BluetoothDevice
////): String {
////
////    return try {
////
////        device.name
////            ?: "Unknown Device"
////
////    } catch (
////        e: SecurityException
////    ) {
////
////        "Unknown Device"
////    }
////}
