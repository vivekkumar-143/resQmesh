package com.resqmesh.app.ui

import android.Manifest
import android.content.pm.PackageManager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

import com.resqmesh.app.database.ResQMeshDatabase
import com.resqmesh.app.model.EmergencyPacket
import com.resqmesh.app.model.EmergencyPacketEntity
import com.resqmesh.app.network.NearbyMeshManager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

import java.util.UUID


// =====================================================
// EMERGENCY COLORS
// =====================================================

private val EmergencyBackground = Color(0xFF110F0F)
private val EmergencySurface = Color(0xFF1D1918)
private val EmergencyText = Color(0xFFF3F0E8)
private val EmergencyMuted = Color(0xFFA9A69D)
private val EmergencyRed = Color(0xFFE53935)
private val EmergencyBorder = Color(0xFF3A302E)


@Composable
fun EmergencyScreen(
    onCancel: () -> Unit
) {

    val context = LocalContext.current

    // =================================================
    // NEARBY MESH MANAGER
    // =================================================

    val nearbyMeshManager = remember {
        NearbyMeshManager(context)
    }


    // =================================================
    // LOCATION
    // =================================================

    var latitude by remember {
        mutableStateOf<Double?>(null)
    }

    var longitude by remember {
        mutableStateOf<Double?>(null)
    }


    // =================================================
    // COUNTDOWN
    // =================================================

    var countdown by remember {
        mutableStateOf(5)
    }


    // =================================================
    // EMERGENCY PACKET
    // =================================================

    var emergencyPacket by remember {
        mutableStateOf<EmergencyPacket?>(null)
    }


    // =================================================
    // GET CURRENT LOCATION
    // =================================================

    LaunchedEffect(Unit) {

        val hasLocationPermission =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

        if (hasLocationPermission) {

            LocationServices
                .getFusedLocationProviderClient(context)
                .lastLocation
                .addOnSuccessListener { location ->

                    if (location != null) {

                        latitude = location.latitude
                        longitude = location.longitude

                    }
                }
        }
    }


    // =================================================
    // 5 SECOND SOS COUNTDOWN
    // =================================================

    LaunchedEffect(Unit) {

        while (countdown > 0) {

            delay(1000)

            countdown--
        }

        // =============================================
        // CREATE SOS PACKET
        // =============================================

        emergencyPacket = EmergencyPacket(

            packetId = UUID.randomUUID().toString(),

            senderDeviceId =
                "LOCAL_DEVICE",

            messageType =
                "PERSON_IN_DANGER",

            latitude =
                latitude,

            longitude =
                longitude,

            severity =
                "CRITICAL",

            message =
                "Help required at current location",

            timestamp =
                System.currentTimeMillis(),

            hopCount =
                0,

            ttl =
                5
        )
    }


    // =================================================
    // SAVE + BROADCAST SOS PACKET
    // =================================================

    LaunchedEffect(emergencyPacket) {

        val packet =
            emergencyPacket
                ?: return@LaunchedEffect


        // =============================================
        // 1. SAVE PACKET LOCALLY
        // =============================================

        val entity =
            EmergencyPacketEntity(

                packetId =
                    packet.packetId,

                senderDeviceId =
                    packet.senderDeviceId,

                messageType =
                    packet.messageType,

                latitude =
                    packet.latitude,

                longitude =
                    packet.longitude,

                severity =
                    packet.severity,

                message =
                    packet.message,

                timestamp =
                    packet.timestamp,

                hopCount =
                    packet.hopCount,

                ttl =
                    packet.ttl,

                transmitted =
                    false
            )


        withContext(Dispatchers.IO) {

            val database =
                ResQMeshDatabase
                    .getDatabase(context)

            database
                .emergencyPacketDao()
                .insertPacket(entity)
        }


        // =============================================
        // 2. SEND PACKET TO NEARBY DEVICES
        // =============================================

        nearbyMeshManager.sendEmergencyPacket(

            packetId =
                packet.packetId,

            senderDeviceId =
                packet.senderDeviceId,

            messageType =
                packet.messageType,

            latitude =
                packet.latitude,

            longitude =
                packet.longitude,

            severity =
                packet.severity,

            message =
                packet.message,

            timestamp =
                packet.timestamp,

            hopCount =
                packet.hopCount,

            ttl =
                packet.ttl
        )
    }


    // =================================================
    // UI
    // =================================================

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EmergencyBackground)
            .padding(20.dp)
    ) {

        Column(
            modifier =
                Modifier.fillMaxSize(),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {


            // =========================================
            // HEADER
            // =========================================

            Spacer(
                modifier =
                    Modifier.height(30.dp)
            )

            Text(
                text =
                    "EMERGENCY MODE",

                color =
                    EmergencyRed,

                fontSize =
                    13.sp,

                fontWeight =
                    FontWeight.Bold,

                letterSpacing =
                    2.sp
            )


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            Text(
                text =
                    "I'M IN DANGER",

                color =
                    EmergencyText,

                fontSize =
                    30.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )


            Text(
                text =
                    if (emergencyPacket == null) {
                        "ResQMesh is preparing an emergency broadcast."
                    } else {
                        "Emergency broadcast is being sent."
                    },

                color =
                    EmergencyMuted,

                fontSize =
                    14.sp
            )


            Spacer(
                modifier =
                    Modifier.height(35.dp)
            )


            // =========================================
            // COUNTDOWN CIRCLE
            // =========================================

            Box(
                modifier =
                    Modifier
                        .size(170.dp)
                        .background(
                            EmergencyRed.copy(
                                alpha = 0.12f
                            ),
                            RoundedCornerShape(85.dp)
                        )
                        .border(
                            2.dp,
                            EmergencyRed,
                            RoundedCornerShape(85.dp)
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        text =
                            if (countdown > 0) {
                                "$countdown"
                            } else {
                                "SOS"
                            },

                        color =
                            EmergencyRed,

                        fontSize =
                            38.sp,

                        fontWeight =
                            FontWeight.Bold
                    )


                    Text(
                        text =
                            if (countdown > 0) {
                                "STARTING..."
                            } else {
                                "BROADCAST"
                            },

                        color =
                            EmergencyMuted,

                        fontSize =
                            10.sp,

                        fontWeight =
                            FontWeight.Bold,

                        letterSpacing =
                            1.sp
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(35.dp)
            )


            // =========================================
            // LOCATION STATUS
            // =========================================

            StatusCard(
                title =
                    "LOCATION",

                value =
                    if (
                        latitude != null &&
                        longitude != null
                    ) {

                        "GPS LOCKED • ${
                            "%.5f".format(latitude)
                        }, ${
                            "%.5f".format(longitude)
                        }"

                    } else {

                        "Waiting for GPS..."

                    }
            )


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            // =========================================
            // MESH STATUS
            // =========================================

            StatusCard(
                title =
                    "MESH NETWORK",

                value =
                    if (emergencyPacket != null) {

                        "SOS BROADCAST STARTED"

                    } else {

                        "Preparing emergency broadcast..."

                    }
            )


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            // =========================================
            // MESSAGE
            // =========================================

            StatusCard(
                title =
                    "EMERGENCY MESSAGE",

                value =
                    "Help required at current location"
            )


            Spacer(
                modifier =
                    Modifier.weight(1f)
            )


            // =========================================
            // CANCEL BUTTON
            // =========================================

            Button(
                onClick =
                    onCancel,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),

                shape =
                    RoundedCornerShape(16.dp),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            EmergencySurface,

                        contentColor =
                            EmergencyText
                    )
            ) {

                Text(
                    text =
                        "CANCEL EMERGENCY",

                    fontWeight =
                        FontWeight.Bold,

                    letterSpacing =
                        1.sp
                )
            }


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            Text(
                text =
                    "No internet connection required",

                color =
                    EmergencyMuted,

                fontSize =
                    12.sp
            )


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )
        }
    }
}


// =====================================================
// STATUS CARD
// =====================================================

@Composable
private fun StatusCard(
    title: String,
    value: String
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    EmergencySurface,
                    RoundedCornerShape(15.dp)
                )
                .border(
                    1.dp,
                    EmergencyBorder,
                    RoundedCornerShape(15.dp)
                )
                .padding(16.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .background(
                        EmergencyRed,
                        RoundedCornerShape(5.dp)
                    )
        )


        Spacer(
            modifier =
                Modifier.size(12.dp)
        )


        Column {

            Text(
                text =
                    title,

                color =
                    EmergencyMuted,

                fontSize =
                    10.sp,

                fontWeight =
                    FontWeight.Bold,

                letterSpacing =
                    1.sp
            )


            Spacer(
                modifier =
                    Modifier.height(3.dp)
            )


            Text(
                text =
                    value,

                color =
                    EmergencyText,

                fontSize =
                    14.sp
            )
        }
    }
}