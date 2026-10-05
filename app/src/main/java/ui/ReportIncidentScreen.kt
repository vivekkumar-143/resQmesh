package com.resqmesh.app.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqmesh.app.model.IncidentType
import com.resqmesh.app.model.Severity

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import com.resqmesh.app.database.ResQMeshDatabase
import com.resqmesh.app.model.IncidentEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

private val ResqBackground = Color(0xFF11110F)
private val ResqSurface = Color(0xFF1B1B18)
private val ResqSurfaceLight = Color(0xFF252521)
private val ResqText = Color(0xFFF3F0E8)
private val ResqMuted = Color(0xFFA9A69D)
private val ResqAmber = Color(0xFFFFA726)
private val ResqRed = Color(0xFFE53935)
private val ResqBorder = Color(0xFF383831)

@Composable
fun ReportIncidentScreen() {

    val context = androidx.compose.ui.platform.LocalContext.current

    val fusedLocationClient: FusedLocationProviderClient =
        remember {
            LocationServices.getFusedLocationProviderClient(context)
        }

    var currentLatitude by remember {
        mutableStateOf<Double?>(null)
    }

    var currentLongitude by remember {
        mutableStateOf<Double?>(null)
    }

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            locationPermissionGranted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }

    LaunchedEffect(Unit) {

        if (!locationPermissionGranted) {

            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(locationPermissionGranted) {

        if (locationPermissionGranted) {

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->

                    if (location != null) {

                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                    }
                }
        }
    }

    var selectedType by remember {
        mutableStateOf(IncidentType.OTHER)
    }

    var selectedSeverity by remember {
        mutableStateOf(Severity.MEDIUM)
    }

    var description by remember {
        mutableStateOf("")
    }

    var reportSaved by remember {
        mutableStateOf(false)
    }

    if (reportSaved) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF1D2A20),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    1.dp,
                    Color(0xFF6FAF7A),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {

            Column {

                Text(
                    text = "✓  REPORT SAVED OFFLINE",
                    color = Color(0xFF9BE7A5),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Text(
                    text = "Your report is stored on this device and can be shared when a connection becomes available.",
                    color = ResqMuted,
                    fontSize = 13.sp
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ResqBackground)
    ) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item {

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text = "REPORT",
                            color = ResqAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "What happened?",
                            color = ResqText,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(5.dp))

                        Text(
                            text = "Send information that could help someone nearby.",
                            color = ResqMuted,
                            fontSize = 14.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = Color(0xFF2A2117),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            text = "!",
                            color = ResqAmber,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {

                Text(
                    text = "INCIDENT TYPE",
                    color = ResqMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            items(
                IncidentType.values().toList().chunked(2)
            ) { rowItems ->

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    rowItems.forEach { type ->

                        IncidentTypeCard(
                            type = type,
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (rowItems.size == 1) {
                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (reportSaved) {

                item {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0xFF1D2A20),
                                RoundedCornerShape(16.dp)
                            )
                            .border(
                                1.dp,
                                Color(0xFF6FAF7A),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {

                        Column {

                            Text(
                                text = "✓  REPORT SAVED OFFLINE",
                                color = Color(0xFF9BE7A5),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Your report is stored on this device and can be shared when a connection becomes available.",
                                color = ResqMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            item {

                Text(
                    text = "DESCRIPTION",
                    color = ResqMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Tell us what is happening...",
                            color = ResqMuted
                        )
                    },
                    minLines = 4,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            item {

                Text(
                    text = "SEVERITY",
                    color = ResqMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Severity.values().forEach { severity ->

                        SeverityButton(
                            severity = severity,
                            selected = selectedSeverity == severity,
                            onClick = {
                                selectedSeverity = severity
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                LocationCard(
                    locationAvailable =
                        currentLatitude != null &&
                                currentLongitude != null
                )
            }

            item {

                Button(
                    onClick = {

                        val incident = IncidentEntity(
                            id = UUID.randomUUID().toString(),
                            type = selectedType.name,
                            description = description,
                            latitude = currentLatitude,
                            longitude = currentLongitude,
                            severity = selectedSeverity.name,
                            timestamp = System.currentTimeMillis(),
                            sourceDeviceId = "LOCAL_DEVICE",
                            verified = false,
                            synced = false,
                            expiresAt = null
                        )

                        CoroutineScope(Dispatchers.IO).launch {

                            val database = ResQMeshDatabase.getDatabase(context)

                            database.incidentDao().insertIncident(incident)

                            CoroutineScope(Dispatchers.Main).launch {
                                reportSaved = true
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ResqAmber,
                        contentColor = Color.Black
                    )
                ) {

                    Text(
                        text = "SEND REPORT",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                TextButton(
                    onClick = {
                        // Back navigation will be connected later.
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text(
                        text = "Cancel",
                        color = ResqMuted
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun IncidentTypeCard(
    type: IncidentType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val background =
        if (selected) Color(0xFF332717)
        else ResqSurface

    val border =
        if (selected) ResqAmber
        else ResqBorder

    Column(
        modifier = modifier
            .height(76.dp)
            .background(
                color = background,
                shape = RoundedCornerShape(15.dp)
            )
            .border(
                width = 1.dp,
                color = border,
                shape = RoundedCornerShape(15.dp)
            )
            .clickable {
                onClick()
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = incidentSymbol(type),
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = type.name
                .replace("_", " ")
                .lowercase()
                .replaceFirstChar {
                    it.uppercase()
                },
            color = if (selected) ResqAmber else ResqText,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SeverityButton(
    severity: Severity,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {

    val color = when (severity) {
        Severity.LOW -> Color(0xFF8BC34A)
        Severity.MEDIUM -> ResqAmber
        Severity.HIGH -> Color(0xFFFF7043)
        Severity.CRITICAL -> ResqRed
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .background(
                color = if (selected) color.copy(alpha = 0.18f)
                else ResqSurface,
                shape = RoundedCornerShape(13.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) color else ResqBorder,
                shape = RoundedCornerShape(13.dp)
            )
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = severity.name,
            color = if (selected) color else ResqMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LocationCard(
    locationAvailable: Boolean
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                ResqSurface,
                RoundedCornerShape(16.dp)
            )
            .border(
                1.dp,
                ResqBorder,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    Color(0xFF2A2117),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {

            Text(
                text = "⌖",
                color = ResqAmber,
                fontSize = 23.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {

            Text(
                text = if (locationAvailable)
                    "LOCATION • READY"
                else
                    "LOCATION • WAITING",
                color = ResqMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = if (locationAvailable)
                    "Location attached to this report"
                else
                    "Waiting for device location",
                color = ResqText,
                fontSize = 14.sp
            )
        }
    }
}

private fun incidentSymbol(type: IncidentType): String {

    return when (type) {

        IncidentType.FIRE -> "🔥"
        IncidentType.FLOOD -> "🌊"
        IncidentType.ROAD_BLOCKED -> "⛔"
        IncidentType.ACCIDENT -> "⚠"
        IncidentType.BUILDING_COLLAPSE -> "🏚"
        IncidentType.MEDICAL_EMERGENCY -> "✚"
        IncidentType.DANGEROUS_AREA -> "☠"
        IncidentType.MISSING_PERSON -> "◉"
        IncidentType.PEOPLE_TRAPPED -> "!"
        IncidentType.NATURAL_DISASTER -> "⚡"
        IncidentType.OTHER -> "＋"
    }
}