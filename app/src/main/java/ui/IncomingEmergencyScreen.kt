package com.resqmesh.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqmesh.app.model.EmergencyPacketEntity

private val Background = Color(0xFF11110F)
private val Surface = Color(0xFF1B1B18)
private val Border = Color(0xFF383831)
private val TextPrimary = Color(0xFFF3F0E8)
private val Muted = Color(0xFFA9A69D)
private val Red = Color(0xFFE53935)
private val Amber = Color(0xFFFFA726)

@Composable
fun IncomingEmergencyScreen(
    packet: EmergencyPacketEntity,
    onAcknowledge: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "🚨",
            fontSize = 56.sp
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "INCOMING EMERGENCY",
            color = Red,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "RESQMESH ALERT",
            color = Amber,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Surface,
                    RoundedCornerShape(20.dp)
                )
                .border(
                    1.dp,
                    Border,
                    RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {

            Text(
                text = "SEVERITY",
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = packet.severity,
                color = Red,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Text(
                text = "EMERGENCY MESSAGE",
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = packet.message,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Text(
                text = "SENDER DEVICE",
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = packet.senderDeviceId,
                color = TextPrimary,
                fontSize = 15.sp
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "LATITUDE",
                        color = Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = packet.latitude?.toString() ?: "Unavailable",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "LONGITUDE",
                        color = Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = packet.longitude?.toString() ?: "Unavailable",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = "📡 Received through nearby ResQMesh network",
                color = Muted,
                fontSize = 12.sp
            )
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onAcknowledge,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Amber,
                contentColor = Background
            )
        ) {

            Text(
                text = "✓  ACKNOWLEDGE ALERT",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Packet ID: ${packet.packetId}",
            color = Color(0xFF77756E),
            fontSize = 9.sp
        )
    }
}