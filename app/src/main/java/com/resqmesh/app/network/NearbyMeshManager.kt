

package com.resqmesh.app.network

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.resqmesh.app.database.ResQMeshDatabase
import com.resqmesh.app.model.EmergencyPacketEntity
import com.resqmesh.app.model.MeshMessage
import com.resqmesh.app.model.MeshMessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SharedLocation(
    val shareId: String,
    val senderDeviceId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val hopCount: Int = 0,
    val ttl: Int = 5
)

class NearbyMeshManager(
    private val context: Context
) {

    companion object {

        private const val TAG = "ResQMeshBluetooth"

        private const val SERVICE_NAME = "ResQMesh"

        private val SERVICE_UUID: UUID =
            UUID.fromString(
                "8f7b2c10-5f2d-4c89-9a10-7b5f0d8e1234"
            )

        private const val MESSAGE_TYPE_TEXT = "MESH_MESSAGE"
        private const val MESSAGE_TYPE_SOS = "SOS"
        private const val MESSAGE_TYPE_PERSON_IN_DANGER = "PERSON_IN_DANGER"
        private const val MESSAGE_TYPE_LOCATION_SHARE = "LOCATION_SHARE"

        private const val DEFAULT_TTL = 5
        private const val HEARTBEAT_INTERVAL_MS = 15000L
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private val database =
        ResQMeshDatabase.getDatabase(context)

    private val localDeviceId =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "UNKNOWN"

    private val localDeviceName =
        "ResQMesh-${localDeviceId.takeLast(6)}"

    /*
     * Active Bluetooth sockets.
     *
     * Key   = remote Bluetooth address
     * Value = active socket
     */
    private val connectedSockets =
        ConcurrentHashMap<String, BluetoothSocket>()

    /*
     * IMPORTANT:
     * Keep one permanent writer for every socket.
     *
     * We do NOT create a new PrintWriter every time
     * a message is sent.
     */
    private val connectedWriters =
        ConcurrentHashMap<String, PrintWriter>()

    /* Prevent duplicate location packets from being shown/relayed. */
    private val receivedLocationShareIds =
        ConcurrentHashMap.newKeySet<String>()

    private var serverSocket: BluetoothServerSocket? = null

    private var serverThread: Thread? = null

    private var meshStarted = false

    /*
     * The device selected when this phone joins a mesh.
     * Keeping this reference lets the client reconnect automatically
     * if the RFCOMM socket is closed by Android/the remote device.
     */
    private var joinedDevice: BluetoothDevice? = null

    private val heartbeatHandler = Handler(Looper.getMainLooper())

    private var heartbeatRunnable: Runnable? = null

    private var reconnectRunnable: Runnable? = null

    private var reconnectScheduled = false

    var onDeviceCountChanged:
            ((Int) -> Unit)? = null

    var onMessageReceived:
            ((String) -> Unit)? = null

    var onEmergencyPacketReceived:
            ((EmergencyPacketEntity) -> Unit)? = null

    var onLocationReceived:
            ((SharedLocation) -> Unit)? = null


    // ============================================================
    // CREATE MESH / HOST
    // ============================================================

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Synchronized
    fun startMesh() {

        if (meshStarted) {

            Log.d(
                TAG,
                "MESH ALREADY RUNNING"
            )

            return
        }

        meshStarted = true

        Log.d(TAG, "====================================")
        Log.d(TAG, "CREATING RESQMESH NETWORK")
        Log.d(TAG, "LOCAL DEVICE = $localDeviceName")
        Log.d(TAG, "ROLE = HOST")
        Log.d(TAG, "====================================")

        if (bluetoothAdapter == null) {

            Log.e(
                TAG,
                "BLUETOOTH NOT SUPPORTED"
            )

            meshStarted = false

            return
        }

        if (
            !hasPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        ) {

            Log.e(
                TAG,
                "BLUETOOTH_CONNECT PERMISSION MISSING"
            )

            meshStarted = false

            return
        }

        if (!bluetoothAdapter.isEnabled) {

            Log.e(
                TAG,
                "BLUETOOTH IS OFF"
            )

            meshStarted = false

            return
        }

        startBluetoothServer()

        makeDeviceDiscoverable()
        startHeartbeat()
    }


    // ============================================================
    // HOST SERVER
    // ============================================================

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startBluetoothServer() {

        if (serverSocket != null) {

            Log.d(
                TAG,
                "SERVER ALREADY RUNNING"
            )

            return
        }

        try {

            serverSocket =
                bluetoothAdapter
                    ?.listenUsingInsecureRfcommWithServiceRecord(
                        SERVICE_NAME,
                        SERVICE_UUID
                    )

            Log.d(
                TAG,
                "===================================="
            )

            Log.d(
                TAG,
                "RESQMESH HOST SERVER STARTED"
            )

            Log.d(
                TAG,
                "SERVICE = $SERVICE_NAME"
            )

            Log.d(
                TAG,
                "DEVICE = $localDeviceName"
            )

            Log.d(
                TAG,
                "UUID = $SERVICE_UUID"
            )

            Log.d(
                TAG,
                "===================================="
            )

            serverThread =
                Thread {

                    while (
                        meshStarted &&
                        !Thread.currentThread().isInterrupted
                    ) {

                        try {

                            val socket =
                                serverSocket?.accept()

                            if (socket != null) {

                                Log.d(
                                    TAG,
                                    "===================================="
                                )

                                Log.d(
                                    TAG,
                                    "RESQMESH INCOMING CONNECTION"
                                )

                                Log.d(
                                    TAG,
                                    "REMOTE = ${safeDeviceName(socket.remoteDevice)}"
                                )

                                Log.d(
                                    TAG,
                                    "===================================="
                                )

                                registerConnection(socket)
                            }

                        } catch (e: Exception) {

                            if (meshStarted) {

                                Log.e(
                                    TAG,
                                    "SERVER ERROR",
                                    e
                                )
                            }

                            break
                        }
                    }
                }

            serverThread?.start()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "SERVER START FAILED",
                e
            )

            serverSocket = null

            meshStarted = false
        }
    }


    // ============================================================
    // DISCOVERABLE
    // ============================================================

    private fun makeDeviceDiscoverable() {

        if (
            !hasPermission(
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        ) {

            Log.e(
                TAG,
                "BLUETOOTH_ADVERTISE PERMISSION MISSING"
            )

            return
        }

        try {

            Log.d(
                TAG,
                "REQUESTING DISCOVERABLE MODE"
            )

            val intent =
                android.content.Intent(
                    BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE
                )

            intent.putExtra(
                BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                300
            )

            intent.addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            )

            context.startActivity(intent)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "DISCOVERABLE REQUEST FAILED",
                e
            )
        }
    }


    // ============================================================
    // GET PAIRED DEVICES
    // ============================================================

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getPairedDevices(): List<BluetoothDevice> {

        if (
            !hasPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        ) {

            return emptyList()
        }

        return try {

            bluetoothAdapter
                ?.bondedDevices
                ?.toList()
                ?: emptyList()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "FAILED TO GET PAIRED DEVICES",
                e
            )

            emptyList()
        }
    }


    // ============================================================
    // JOIN MESH
    // ============================================================

    fun joinMesh(
        device: BluetoothDevice
    ) {

        if (
            !hasPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        ) {

            Log.e(
                TAG,
                "BLUETOOTH_CONNECT PERMISSION MISSING"
            )

            return
        }

        if (meshStarted) {

            Log.d(
                TAG,
                "MESH ALREADY RUNNING"
            )

            return
        }

        meshStarted = true
        joinedDevice = device
        startHeartbeat()

        Log.d(TAG, "====================================")
        Log.d(TAG, "JOINING RESQMESH NETWORK")
        Log.d(TAG, "LOCAL DEVICE = $localDeviceName")
        Log.d(TAG, "TARGET DEVICE = ${safeDeviceName(device)}")
        Log.d(TAG, "TARGET ADDRESS = ${device.address}")
        Log.d(TAG, "ROLE = JOIN")
        Log.d(TAG, "====================================")

        Thread {

            var socket: BluetoothSocket? = null

            try {

                Log.d(
                    TAG,
                    "CREATING RFCOMM CONNECTION..."
                )

                socket =
                    device.createInsecureRfcommSocketToServiceRecord(
                        SERVICE_UUID
                    )

                socket.connect()

                Log.d(TAG, "====================================")
                Log.d(
                    TAG,
                    "RESQMESH CONNECTION SUCCESS"
                )

                Log.d(
                    TAG,
                    "CONNECTED TO = ${safeDeviceName(device)}"
                )

                Log.d(
                    TAG,
                    "ADDRESS = ${device.address}"
                )

                Log.d(TAG, "====================================")

                registerConnection(socket)

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "RESQMESH JOIN FAILED",
                    e
                )

                try {
                    socket?.close()
                } catch (_: Exception) {
                }

                meshStarted = false
                joinedDevice = null
                stopHeartbeat()
            }

        }.start()
    }


    // ============================================================
    // REGISTER CONNECTION
    // ============================================================

    private fun registerConnection(
        socket: BluetoothSocket
    ) {

        if (
            !hasPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        ) {
            return
        }

        val device =
            socket.remoteDevice

        val address =
            device.address

        val oldSocket =
            connectedSockets.putIfAbsent(
                address,
                socket
            )

        if (oldSocket != null) {

            Log.d(
                TAG,
                "CONNECTION ALREADY EXISTS -> $address"
            )

            try {
                socket.close()
            } catch (_: Exception) {
            }

            return
        }

        /*
         * Create ONE permanent writer for this socket.
         */
        try {

            val writer =
                PrintWriter(
                    OutputStreamWriter(
                        socket.outputStream
                    ),
                    true
                )

            connectedWriters[address] =
                writer

        } catch (e: Exception) {

            Log.e(
                TAG,
                "WRITER CREATION FAILED",
                e
            )

            connectedSockets.remove(address)

            try {
                socket.close()
            } catch (_: Exception) {
            }

            return
        }

        Log.d(TAG, "====================================")

        Log.d(
            TAG,
            "RESQMESH NODE CONNECTED"
        )

        Log.d(
            TAG,
            "REMOTE = ${safeDeviceName(device)}"
        )

        Log.d(
            TAG,
            "ADDRESS = $address"
        )

        Log.d(
            TAG,
            "CONNECTED NODES = ${connectedSockets.size}"
        )

        Log.d(TAG, "====================================")

        onDeviceCountChanged?.invoke(
            connectedSockets.size
        )

        /*
         * Start receiving messages.
         */
        startReader(
            socket,
            address
        )

        /*
         * Once connected, send any messages
         * that were waiting locally.
         */
        sendPendingMessagesToNode(address)
    }


    // ============================================================
    // READ MESSAGES
    // ============================================================

    private fun startReader(
        socket: BluetoothSocket,
        address: String
    ) {

        Thread {

            try {

                val reader =
                    BufferedReader(
                        InputStreamReader(
                            socket.inputStream
                        )
                    )

                while (meshStarted) {

                    val message =
                        reader.readLine()
                            ?: break

                    if (message.isBlank()) {
                        continue
                    }

                    Log.d(
                        TAG,
                        "===================================="
                    )

                    Log.d(
                        TAG,
                        "MESSAGE RECEIVED FROM = $address"
                    )

                    Log.d(
                        TAG,
                        message
                    )

                    Log.d(
                        TAG,
                        "===================================="
                    )

                    processReceivedMessage(
                        message,
                        address
                    )
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "READ ERROR FROM $address : ${e.message}"
                )

            } finally {

                removeConnection(address, socket)
            }

        }.start()
    }


    // ============================================================
    // REMOVE CONNECTION
    // ============================================================

    private fun removeConnection(
        address: String,
        socket: BluetoothSocket? = null
    ) {

        /*
         * A reader belonging to an OLD socket must never remove a newer
         * connection that has already been established for the same device.
         */
        val currentSocket = connectedSockets[address]

        if (
            socket != null &&
            currentSocket != null &&
            currentSocket !== socket
        ) {
            Log.d(
                TAG,
                "IGNORING OLD SOCKET DISCONNECT -> $address"
            )
            return
        }

        connectedWriters.remove(address)

        val removedSocket =
            connectedSockets.remove(address)

        try {
            removedSocket?.close()
        } catch (_: Exception) {
        }

        onDeviceCountChanged?.invoke(
            connectedSockets.size
        )

        Log.d(
            TAG,
            "NODE DISCONNECTED -> $address"
        )

        Log.d(
            TAG,
            "CONNECTED NODES = ${connectedSockets.size}"
        )

        /*
         * If this phone is the JOINER, automatically try to restore the
         * connection. The HOST continues listening, so no user action is
         * required after a temporary RFCOMM drop.
         */
        if (
            meshStarted &&
            joinedDevice != null
        ) {
            scheduleReconnect()
        }
    }


    // ============================================================
    // AUTOMATIC RECONNECT
    // ============================================================

    private fun scheduleReconnect() {

        if (reconnectScheduled) {
            return
        }

        val device = joinedDevice ?: return

        reconnectScheduled = true

        reconnectRunnable = Runnable {

            reconnectScheduled = false

            if (!meshStarted) {
                return@Runnable
            }

            if (connectedSockets.isNotEmpty()) {
                return@Runnable
            }

            if (
                !hasPermission(
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            ) {
                Log.e(
                    TAG,
                    "RECONNECT SKIPPED - BLUETOOTH_CONNECT PERMISSION MISSING"
                )
                return@Runnable
            }

            Log.d(
                TAG,
                "===================================="
            )

            Log.d(
                TAG,
                "RESQMESH AUTOMATIC RECONNECT"
            )

            Log.d(
                TAG,
                "TARGET = ${safeDeviceName(device)}"
            )

            Log.d(
                TAG,
                "ADDRESS = ${device.address}"
            )

            Log.d(
                TAG,
                "===================================="
            )

            Thread {

                var socket: BluetoothSocket? = null

                try {

                    socket =
                        device.createInsecureRfcommSocketToServiceRecord(
                            SERVICE_UUID
                        )

                    socket.connect()

                    if (!meshStarted) {
                        try {
                            socket.close()
                        } catch (_: Exception) {
                        }
                        return@Thread
                    }

                    Log.d(
                        TAG,
                        "RESQMESH AUTOMATIC RECONNECT SUCCESS"
                    )

                    registerConnection(socket)

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "AUTOMATIC RECONNECT FAILED: ${e.message}"
                    )

                    try {
                        socket?.close()
                    } catch (_: Exception) {
                    }

                    if (meshStarted && joinedDevice != null) {
                        reconnectScheduled = false
                        scheduleReconnect()
                    }
                }

            }.start()
        }

        heartbeatHandler.postDelayed(
            reconnectRunnable!!,
            RECONNECT_DELAY_MS
        )
    }


    // ============================================================
    // CONNECTION HEARTBEAT
    // ============================================================

    private fun startHeartbeat() {

        if (heartbeatRunnable != null) {
            return
        }

        heartbeatRunnable = object : Runnable {

            override fun run() {

                if (!meshStarted) {
                    return
                }

                sendHeartbeat()

                heartbeatHandler.postDelayed(
                    this,
                    HEARTBEAT_INTERVAL_MS
                )
            }
        }

        heartbeatHandler.postDelayed(
            heartbeatRunnable!!,
            HEARTBEAT_INTERVAL_MS
        )
    }


    private fun stopHeartbeat() {

        heartbeatRunnable?.let {
            heartbeatHandler.removeCallbacks(it)
        }

        heartbeatRunnable = null
    }


    private fun sendHeartbeat() {

        if (connectedWriters.isEmpty()) {
            return
        }

        val heartbeat =
            JSONObject().apply {
                put(
                    "messageType",
                    "MESH_HEARTBEAT"
                )
                put(
                    "timestamp",
                    System.currentTimeMillis()
                )
            }.toString()

        connectedWriters.forEach { (address, writer) ->

            Thread {

                try {

                    synchronized(writer) {
                        writer.println(heartbeat)
                        writer.flush()
                    }

                    Log.d(
                        TAG,
                        "HEARTBEAT SENT -> $address"
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "HEARTBEAT FAILED -> $address : ${e.message}"
                    )

                    removeConnection(address)
                }
            }.start()
        }
    }


    // ============================================================
    // GENERIC MESSAGE SEND
    // ============================================================

    fun sendMessage(
        message: String
    ) {

        sendMessage(
            message = message,
            excludeAddress = null
        )
    }


    private fun sendMessage(
        message: String,
        excludeAddress: String?
    ) {

        if (connectedSockets.isEmpty()) {

            Log.w(
                TAG,
                "NO RESQMESH NODES CONNECTED"
            )

            return
        }

        connectedWriters.forEach { (address, writer) ->

            /*
             * Never immediately send a received message
             * back to the node that sent it.
             */
            if (
                excludeAddress != null &&
                address == excludeAddress
            ) {
                return@forEach
            }

            Thread {

                try {

                    synchronized(writer) {

                        writer.println(message)

                        writer.flush()
                    }

                    Log.d(
                        TAG,
                        "MESSAGE SENT -> $address"
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "SEND FAILED -> $address",
                        e
                    )

                    removeConnection(address)
                }

            }.start()
        }
    }


    // ============================================================
    // PUBLIC TEXT MESSAGE API
    // ============================================================

    fun sendTextMessage(
        message: String
    ) {

        if (message.isBlank()) {

            Log.w(
                TAG,
                "EMPTY MESSAGE IGNORED"
            )

            return
        }

        val meshMessage =
            MeshMessage(
                messageId = UUID.randomUUID().toString(),
                senderDeviceId = localDeviceId,
                message = message,
                timestamp = System.currentTimeMillis(),
                hopCount = 0,
                ttl = DEFAULT_TTL
            )

        /*
         * Save locally first.
         */
        CoroutineScope(
            Dispatchers.IO
        ).launch {

            val entity =
                MeshMessageEntity(
                    messageId =
                        meshMessage.messageId,

                    senderDeviceId =
                        meshMessage.senderDeviceId,

                    message =
                        meshMessage.message,

                    timestamp =
                        meshMessage.timestamp,

                    hopCount =
                        meshMessage.hopCount,

                    ttl =
                        meshMessage.ttl,

                    transmitted =
                        false
                )

            database
                .meshMessageDao()
                .insertMessage(entity)

            /*
             * Then transmit.
             */
            withContext(
                Dispatchers.Main
            ) {

                val json =
                    meshMessageToJson(
                        meshMessage
                    )

                Log.d(
                    TAG,
                    "===================================="
                )

                Log.d(
                    TAG,
                    "SENDING MESH TEXT MESSAGE"
                )

                Log.d(
                    TAG,
                    "MESSAGE ID = ${meshMessage.messageId}"
                )

                Log.d(
                    TAG,
                    "MESSAGE = ${meshMessage.message}"
                )

                Log.d(
                    TAG,
                    "CONNECTED NODES = ${connectedSockets.size}"
                )

                Log.d(
                    TAG,
                    "===================================="
                )

                sendMessage(
                    json.toString()
                )
            }
        }
    }


    // ============================================================
    // CONVERT MESH MESSAGE -> JSON
    // ============================================================

    private fun meshMessageToJson(
        message: MeshMessage
    ): JSONObject {

        return JSONObject().apply {

            put(
                "messageType",
                MESSAGE_TYPE_TEXT
            )

            put(
                "messageId",
                message.messageId
            )

            put(
                "senderDeviceId",
                message.senderDeviceId
            )

            put(
                "message",
                message.message
            )

            put(
                "timestamp",
                message.timestamp
            )

            put(
                "hopCount",
                message.hopCount
            )

            put(
                "ttl",
                message.ttl
            )
        }
    }


    // ============================================================
    // PROCESS RECEIVED MESSAGE
    // ============================================================

    private fun processReceivedMessage(
        rawMessage: String,
        sourceAddress: String
    ) {

        try {

            val json =
                JSONObject(rawMessage)

            val messageType =
                json.optString(
                    "messageType",
                    "UNKNOWN"
                )

            /*
             * Heartbeat is only a connection-keepalive packet.
             * Never expose it to the application UI or relay it.
             */
            if (messageType == "MESH_HEARTBEAT") {
                Log.d(TAG, "HEARTBEAT RECEIVED FROM = $sourceAddress")
                return
            }

            /*
             * ====================================================
             * NORMAL MESH TEXT MESSAGE
             * ====================================================
             */

            if (
                messageType == MESSAGE_TYPE_TEXT
            ) {

                processMeshTextMessage(
                    json,
                    sourceAddress
                )

                return
            }

            /*
             * ====================================================
             * SHARED LOCATION
             * ====================================================
             */

            if (
                messageType == MESSAGE_TYPE_LOCATION_SHARE
            ) {

                processLocationShare(
                    json,
                    sourceAddress
                )

                return
            }

            /*
             * ====================================================
             * SOS MESSAGE
             * ====================================================
             */

            if (
                messageType == MESSAGE_TYPE_SOS ||
                messageType == MESSAGE_TYPE_PERSON_IN_DANGER
            ) {

                processEmergencyMessage(
                    json,
                    sourceAddress
                )

                return
            }

            /*
             * Unknown message type.
             */
            Handler(
                Looper.getMainLooper()
            ).post {

                onMessageReceived?.invoke(
                    rawMessage
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "MESSAGE PROCESSING FAILED",
                e
            )
        }
    }


    // ============================================================
    // PROCESS NORMAL MESH TEXT
    // ============================================================

    private fun processMeshTextMessage(
        json: JSONObject,
        sourceAddress: String
    ) {

        val messageId =
            json.optString(
                "messageId"
            )

        if (messageId.isBlank()) {

            Log.w(
                TAG,
                "MESSAGE WITHOUT ID IGNORED"
            )

            return
        }

        val senderDeviceId =
            json.optString(
                "senderDeviceId",
                "UNKNOWN"
            )

        val message =
            json.optString(
                "message",
                ""
            )

        val timestamp =
            json.optLong(
                "timestamp",
                System.currentTimeMillis()
            )

        val hopCount =
            json.optInt(
                "hopCount",
                0
            )

        val ttl =
            json.optInt(
                "ttl",
                DEFAULT_TTL
            )

        CoroutineScope(
            Dispatchers.IO
        ).launch {

            /*
             * ====================================================
             * DUPLICATE PROTECTION
             * ====================================================
             */

            val existing =
                database
                    .meshMessageDao()
                    .getMessage(
                        messageId
                    )

            if (existing != null) {

                Log.d(
                    TAG,
                    "DUPLICATE MESSAGE IGNORED -> $messageId"
                )

                return@launch
            }

            /*
             * ====================================================
             * SAVE RECEIVED MESSAGE
             * ====================================================
             */

            val entity =
                MeshMessageEntity(
                    messageId =
                        messageId,

                    senderDeviceId =
                        senderDeviceId,

                    message =
                        message,

                    timestamp =
                        timestamp,

                    hopCount =
                        hopCount,

                    ttl =
                        ttl,

                    transmitted =
                        false
                )

            database
                .meshMessageDao()
                .insertMessage(
                    entity
                )

            /*
             * ====================================================
             * SHOW MESSAGE TO UI
             * ====================================================
             */

            withContext(
                Dispatchers.Main
            ) {

                onMessageReceived?.invoke(
                    message
                )
            }

            /*
             * ====================================================
             * MULTI-HOP RELAY
             * ====================================================
             *
             * Example:
             *
             * A -> B -> C -> D
             *
             * TTL prevents infinite forwarding.
             */

            if (ttl > 0) {

                val relay =
                    JSONObject().apply {

                        put(
                            "messageType",
                            MESSAGE_TYPE_TEXT
                        )

                        put(
                            "messageId",
                            messageId
                        )

                        put(
                            "senderDeviceId",
                            senderDeviceId
                        )

                        put(
                            "message",
                            message
                        )

                        put(
                            "timestamp",
                            timestamp
                        )

                        put(
                            "hopCount",
                            hopCount + 1
                        )

                        put(
                            "ttl",
                            ttl - 1
                        )
                    }

                sendMessage(
                    message =
                        relay.toString(),
                    excludeAddress =
                        sourceAddress
                )

                database
                    .meshMessageDao()
                    .markAsTransmitted(
                        messageId
                    )

                Log.d(
                    TAG,
                    "MESH MESSAGE RELAYED -> $messageId"
                )
            }
        }
    }


    // ============================================================
    // SEND PENDING MESSAGES TO NEW NODE
    // ============================================================

    private fun sendPendingMessagesToNode(
        address: String
    ) {

        CoroutineScope(
            Dispatchers.IO
        ).launch {

            val pending =
                database
                    .meshMessageDao()
                    .getPendingMessages()

            if (pending.isEmpty()) {

                Log.d(
                    TAG,
                    "NO PENDING MESH MESSAGES"
                )

                return@launch
            }

            val writer =
                connectedWriters[address]

            if (writer == null) {

                Log.w(
                    TAG,
                    "NO WRITER FOR NODE -> $address"
                )

                return@launch
            }

            pending.forEach { entity ->

                try {

                    val json =
                        JSONObject().apply {

                            put(
                                "messageType",
                                MESSAGE_TYPE_TEXT
                            )

                            put(
                                "messageId",
                                entity.messageId
                            )

                            put(
                                "senderDeviceId",
                                entity.senderDeviceId
                            )

                            put(
                                "message",
                                entity.message
                            )

                            put(
                                "timestamp",
                                entity.timestamp
                            )

                            put(
                                "hopCount",
                                entity.hopCount
                            )

                            put(
                                "ttl",
                                entity.ttl
                            )
                        }

                    synchronized(writer) {

                        writer.println(
                            json.toString()
                        )

                        writer.flush()
                    }

                    database
                        .meshMessageDao()
                        .markAsTransmitted(
                            entity.messageId
                        )

                    Log.d(
                        TAG,
                        "PENDING MESSAGE SENT -> ${entity.messageId}"
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "PENDING MESSAGE SEND FAILED",
                        e
                    )
                }
            }
        }
    }


    // ============================================================
    // SHARE CURRENT LOCATION
    // ============================================================

    fun sendLocationShare(
        latitude: Double,
        longitude: Double
    ) {

        val shareId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val json =
            JSONObject().apply {
                put("shareId", shareId)
                put("senderDeviceId", localDeviceId)
                put("messageType", MESSAGE_TYPE_LOCATION_SHARE)
                put("latitude", latitude)
                put("longitude", longitude)
                put("timestamp", timestamp)
                put("hopCount", 0)
                put("ttl", DEFAULT_TTL)
            }

        Log.d(TAG, "====================================")
        Log.d(TAG, "SHARING LOCATION = $shareId")
        Log.d(TAG, "LATITUDE = $latitude")
        Log.d(TAG, "LONGITUDE = $longitude")
        Log.d(TAG, "CONNECTED NODES = ${connectedSockets.size}")
        Log.d(TAG, "====================================")

        receivedLocationShareIds.add(shareId)
        sendMessage(json.toString())
    }


    // ============================================================
    // PROCESS SHARED LOCATION
    // ============================================================

    private fun processLocationShare(
        json: JSONObject,
        sourceAddress: String
    ) {

        val shareId = json.optString("shareId").trim()
        if (shareId.isBlank()) {
            Log.w(TAG, "LOCATION SHARE WITHOUT SHARE ID")
            return
        }

        if (!receivedLocationShareIds.add(shareId)) {
            Log.d(TAG, "DUPLICATE LOCATION SHARE IGNORED -> $shareId")
            return
        }

        val latitude = json.optDouble("latitude", Double.NaN)
        val longitude = json.optDouble("longitude", Double.NaN)

        if (latitude.isNaN() || longitude.isNaN()) {
            Log.w(TAG, "INVALID LOCATION SHARE -> $shareId")
            return
        }

        val sharedLocation = SharedLocation(
            shareId = shareId,
            senderDeviceId = json.optString("senderDeviceId", "UNKNOWN"),
            latitude = latitude,
            longitude = longitude,
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            hopCount = json.optInt("hopCount", 0),
            ttl = json.optInt("ttl", DEFAULT_TTL)
        )

        Log.d(
            TAG,
            "LOCATION RECEIVED FROM $sourceAddress -> " +
                    "${sharedLocation.latitude}, ${sharedLocation.longitude}"
        )

        Handler(Looper.getMainLooper()).post {
            onLocationReceived?.invoke(sharedLocation)
        }

        if (sharedLocation.ttl > 0) {
            val relay = JSONObject().apply {
                put("shareId", sharedLocation.shareId)
                put("senderDeviceId", sharedLocation.senderDeviceId)
                put("messageType", MESSAGE_TYPE_LOCATION_SHARE)
                put("latitude", sharedLocation.latitude)
                put("longitude", sharedLocation.longitude)
                put("timestamp", sharedLocation.timestamp)
                put("hopCount", sharedLocation.hopCount + 1)
                put("ttl", sharedLocation.ttl - 1)
            }

            sendMessage(
                message = relay.toString(),
                excludeAddress = sourceAddress
            )

            Log.d(TAG, "LOCATION SHARE RELAYED -> $shareId")
        }
    }


    // ============================================================
    // SEND SOS
    // ============================================================

    fun sendEmergencyPacket(
        packetId: String,
        senderDeviceId: String,
        messageType: String,
        latitude: Double?,
        longitude: Double?,
        severity: String,
        message: String,
        timestamp: Long,
        hopCount: Int,
        ttl: Int
    ) {

        val json =
            JSONObject().apply {

                put(
                    "packetId",
                    packetId
                )

                put(
                    "senderDeviceId",
                    senderDeviceId
                )

                put(
                    "messageType",
                    messageType
                )

                put(
                    "latitude",
                    latitude ?: JSONObject.NULL
                )

                put(
                    "longitude",
                    longitude ?: JSONObject.NULL
                )

                put(
                    "severity",
                    severity
                )

                put(
                    "message",
                    message
                )

                put(
                    "timestamp",
                    timestamp
                )

                put(
                    "hopCount",
                    hopCount
                )

                put(
                    "ttl",
                    ttl
                )
            }

        Log.d(
            TAG,
            "===================================="
        )

        Log.d(
            TAG,
            "SENDING SOS PACKET = $packetId"
        )

        Log.d(
            TAG,
            "CONNECTED NODES = ${connectedSockets.size}"
        )

        Log.d(
            TAG,
            "===================================="
        )

        sendMessage(
            json.toString()
        )
    }


    // ============================================================
    // PROCESS SOS
    // ============================================================

    private fun processEmergencyMessage(
        json: JSONObject,
        sourceAddress: String
    ) {

        val packetId =
            json.optString(
                "packetId"
            )

        if (packetId.isBlank()) {
            return
        }

        val senderDeviceId =
            json.optString(
                "senderDeviceId",
                "UNKNOWN"
            )

        val latitude =
            if (
                json.isNull("latitude")
            ) {
                null
            } else {
                json.optDouble("latitude")
            }

        val longitude =
            if (
                json.isNull("longitude")
            ) {
                null
            } else {
                json.optDouble("longitude")
            }

        val severity =
            json.optString(
                "severity",
                "UNKNOWN"
            )

        val emergencyMessage =
            json.optString(
                "message",
                "Emergency reported"
            )

        val messageType =
            json.optString(
                "messageType",
                MESSAGE_TYPE_SOS
            )

        val timestamp =
            json.optLong(
                "timestamp",
                System.currentTimeMillis()
            )

        val hopCount =
            json.optInt(
                "hopCount",
                0
            )

        val ttl =
            json.optInt(
                "ttl",
                DEFAULT_TTL
            )

        CoroutineScope(
            Dispatchers.IO
        ).launch {

            val existing =
                database
                    .emergencyPacketDao()
                    .getPacket(
                        packetId
                    )

            if (existing != null) {

                Log.d(
                    TAG,
                    "DUPLICATE SOS IGNORED -> $packetId"
                )

                return@launch
            }

            val packet =
                EmergencyPacketEntity(

                    packetId =
                        packetId,

                    senderDeviceId =
                        senderDeviceId,

                    messageType =
                        messageType,

                    latitude =
                        latitude,

                    longitude =
                        longitude,

                    severity =
                        severity,

                    message =
                        emergencyMessage,

                    timestamp =
                        timestamp,

                    hopCount =
                        hopCount,

                    ttl =
                        ttl,

                    transmitted =
                        false
                )

            database
                .emergencyPacketDao()
                .insertPacket(
                    packet
                )

            withContext(
                Dispatchers.Main
            ) {

                onEmergencyPacketReceived?.invoke(
                    packet
                )
            }

            /*
             * Relay SOS to other nodes.
             */
            if (ttl > 0) {

                val relay =
                    JSONObject().apply {

                        put(
                            "packetId",
                            packetId
                        )

                        put(
                            "senderDeviceId",
                            senderDeviceId
                        )

                        put(
                            "messageType",
                            messageType
                        )

                        put(
                            "latitude",
                            latitude
                                ?: JSONObject.NULL
                        )

                        put(
                            "longitude",
                            longitude
                                ?: JSONObject.NULL
                        )

                        put(
                            "severity",
                            severity
                        )

                        put(
                            "message",
                            emergencyMessage
                        )

                        put(
                            "timestamp",
                            timestamp
                        )

                        put(
                            "hopCount",
                            hopCount + 1
                        )

                        put(
                            "ttl",
                            ttl - 1
                        )
                    }

                sendMessage(
                    message =
                        relay.toString(),
                    excludeAddress =
                        sourceAddress
                )

                Log.d(
                    TAG,
                    "SOS RELAYED -> $packetId"
                )
            }
        }
    }


    // ============================================================
    // STOP MESH
    // ============================================================

    @Synchronized
    fun stopMesh() {

        Log.d(
            TAG,
            "STOPPING RESQMESH"
        )

        meshStarted = false
        joinedDevice = null

        reconnectRunnable?.let {
            heartbeatHandler.removeCallbacks(it)
        }
        reconnectRunnable = null
        reconnectScheduled = false

        stopHeartbeat()

        try {

            serverSocket?.close()

        } catch (_: Exception) {
        }

        serverSocket = null

        serverThread?.interrupt()

        serverThread = null

        connectedWriters.forEach { (_, writer) ->

            try {
                writer.close()
            } catch (_: Exception) {
            }
        }

        connectedWriters.clear()

        connectedSockets.forEach { (_, socket) ->

            try {
                socket.close()
            } catch (_: Exception) {
            }
        }

        connectedSockets.clear()
        receivedLocationShareIds.clear()

        onDeviceCountChanged?.invoke(
            0
        )

        Log.d(
            TAG,
            "RESQMESH STOPPED"
        )
    }


    // ============================================================
    // DEVICE NAME
    // ============================================================

    private fun safeDeviceName(
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


    // ============================================================
    // PERMISSION
    // ============================================================

    private fun hasPermission(
        permission: String
    ): Boolean {

        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) ==
                PackageManager.PERMISSION_GRANTED
    }
}



///*
//
//
//
//
//package com.resqmesh.app.network
//
//import android.Manifest
//import android.bluetooth.BluetoothAdapter
//import android.bluetooth.BluetoothDevice
//import android.bluetooth.BluetoothServerSocket
//import android.bluetooth.BluetoothSocket
//import android.content.Context
//import android.content.pm.PackageManager
//import android.os.Handler
//import android.os.Looper
//import android.provider.Settings
//import android.util.Log
//import androidx.annotation.RequiresPermission
//import androidx.core.content.ContextCompat
//import com.resqmesh.app.database.ResQMeshDatabase
//import com.resqmesh.app.model.EmergencyPacketEntity
//import com.resqmesh.app.model.MeshMessage
//import com.resqmesh.app.model.MeshMessageEntity
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import org.json.JSONObject
//import java.io.BufferedReader
//import java.io.InputStreamReader
//import java.io.OutputStreamWriter
//import java.io.PrintWriter
//import java.util.UUID
//import java.util.concurrent.ConcurrentHashMap
//
//class NearbyMeshManager(
//    private val context: Context
//) {
//
//    companion object {
//
//        private const val TAG = "ResQMeshBluetooth"
//
//        private const val SERVICE_NAME = "ResQMesh"
//
//        private val SERVICE_UUID: UUID =
//            UUID.fromString(
//                "8f7b2c10-5f2d-4c89-9a10-7b5f0d8e1234"
//            )
//
//        private const val MESSAGE_TYPE_TEXT = "MESH_MESSAGE"
//        private const val MESSAGE_TYPE_SOS = "SOS"
//        private const val MESSAGE_TYPE_PERSON_IN_DANGER = "PERSON_IN_DANGER"
//
//        private const val DEFAULT_TTL = 5
//        private const val HEARTBEAT_INTERVAL_MS = 15000L
//        private const val RECONNECT_DELAY_MS = 3000L
//    }
//
//    private val bluetoothAdapter: BluetoothAdapter? =
//        BluetoothAdapter.getDefaultAdapter()
//
//    private val database =
//        ResQMeshDatabase.getDatabase(context)
//
//    private val localDeviceId =
//        Settings.Secure.getString(
//            context.contentResolver,
//            Settings.Secure.ANDROID_ID
//        ) ?: "UNKNOWN"
//
//    private val localDeviceName =
//        "ResQMesh-${localDeviceId.takeLast(6)}"
//
//    */
///*
//     * Active Bluetooth sockets.
//     *
//     * Key   = remote Bluetooth address
//     * Value = active socket
//     *//*
//
//    private val connectedSockets =
//        ConcurrentHashMap<String, BluetoothSocket>()
//
//    */
///*
//     * IMPORTANT:
//     * Keep one permanent writer for every socket.
//     *
//     * We do NOT create a new PrintWriter every time
//     * a message is sent.
//     *//*
//
//    private val connectedWriters =
//        ConcurrentHashMap<String, PrintWriter>()
//
//    private var serverSocket: BluetoothServerSocket? = null
//
//    private var serverThread: Thread? = null
//
//    private var meshStarted = false
//
//    */
///*
//     * The device selected when this phone joins a mesh.
//     * Keeping this reference lets the client reconnect automatically
//     * if the RFCOMM socket is closed by Android/the remote device.
//     *//*
//
//    private var joinedDevice: BluetoothDevice? = null
//
//    private val heartbeatHandler = Handler(Looper.getMainLooper())
//
//    private var heartbeatRunnable: Runnable? = null
//
//    private var reconnectRunnable: Runnable? = null
//
//    private var reconnectScheduled = false
//
//    var onDeviceCountChanged:
//            ((Int) -> Unit)? = null
//
//    var onMessageReceived:
//            ((String) -> Unit)? = null
//
//    var onEmergencyPacketReceived:
//            ((EmergencyPacketEntity) -> Unit)? = null
//
//
//    // ============================================================
//    // CREATE MESH / HOST
//    // ============================================================
//
//    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
//    @Synchronized
//    fun startMesh() {
//
//        if (meshStarted) {
//
//            Log.d(
//                TAG,
//                "MESH ALREADY RUNNING"
//            )
//
//            return
//        }
//
//        meshStarted = true
//
//        Log.d(TAG, "====================================")
//        Log.d(TAG, "CREATING RESQMESH NETWORK")
//        Log.d(TAG, "LOCAL DEVICE = $localDeviceName")
//        Log.d(TAG, "ROLE = HOST")
//        Log.d(TAG, "====================================")
//
//        if (bluetoothAdapter == null) {
//
//            Log.e(
//                TAG,
//                "BLUETOOTH NOT SUPPORTED"
//            )
//
//            meshStarted = false
//
//            return
//        }
//
//        if (
//            !hasPermission(
//                Manifest.permission.BLUETOOTH_CONNECT
//            )
//        ) {
//
//            Log.e(
//                TAG,
//                "BLUETOOTH_CONNECT PERMISSION MISSING"
//            )
//
//            meshStarted = false
//
//            return
//        }
//
//        if (!bluetoothAdapter.isEnabled) {
//
//            Log.e(
//                TAG,
//                "BLUETOOTH IS OFF"
//            )
//
//            meshStarted = false
//
//            return
//        }
//
//        startBluetoothServer()
//
//        makeDeviceDiscoverable()
//        startHeartbeat()
//    }
//
//
//    // ============================================================
//    // HOST SERVER
//    // ============================================================
//
//    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
//    private fun startBluetoothServer() {
//
//        if (serverSocket != null) {
//
//            Log.d(
//                TAG,
//                "SERVER ALREADY RUNNING"
//            )
//
//            return
//        }
//
//        try {
//
//            serverSocket =
//                bluetoothAdapter
//                    ?.listenUsingInsecureRfcommWithServiceRecord(
//                        SERVICE_NAME,
//                        SERVICE_UUID
//                    )
//
//            Log.d(
//                TAG,
//                "===================================="
//            )
//
//            Log.d(
//                TAG,
//                "RESQMESH HOST SERVER STARTED"
//            )
//
//            Log.d(
//                TAG,
//                "SERVICE = $SERVICE_NAME"
//            )
//
//            Log.d(
//                TAG,
//                "DEVICE = $localDeviceName"
//            )
//
//            Log.d(
//                TAG,
//                "UUID = $SERVICE_UUID"
//            )
//
//            Log.d(
//                TAG,
//                "===================================="
//            )
//
//            serverThread =
//                Thread {
//
//                    while (
//                        meshStarted &&
//                        !Thread.currentThread().isInterrupted
//                    ) {
//
//                        try {
//
//                            val socket =
//                                serverSocket?.accept()
//
//                            if (socket != null) {
//
//                                Log.d(
//                                    TAG,
//                                    "===================================="
//                                )
//
//                                Log.d(
//                                    TAG,
//                                    "RESQMESH INCOMING CONNECTION"
//                                )
//
//                                Log.d(
//                                    TAG,
//                                    "REMOTE = ${safeDeviceName(socket.remoteDevice)}"
//                                )
//
//                                Log.d(
//                                    TAG,
//                                    "===================================="
//                                )
//
//                                registerConnection(socket)
//                            }
//
//                        } catch (e: Exception) {
//
//                            if (meshStarted) {
//
//                                Log.e(
//                                    TAG,
//                                    "SERVER ERROR",
//                                    e
//                                )
//                            }
//
//                            break
//                        }
//                    }
//                }
//
//            serverThread?.start()
//
//        } catch (e: Exception) {
//
//            Log.e(
//                TAG,
//                "SERVER START FAILED",
//                e
//            )
//
//            serverSocket = null
//
//            meshStarted = false
//        }
//    }
//
//
//    // ============================================================
//    // DISCOVERABLE
//    // ============================================================
//
//    private fun makeDeviceDiscoverable() {
//
//        if (
//            !hasPermission(
//                Manifest.permission.BLUETOOTH_ADVERTISE
//            )
//        ) {
//
//            Log.e(
//                TAG,
//                "BLUETOOTH_ADVERTISE PERMISSION MISSING"
//            )
//
//            return
//        }
//
//        try {
//
//            Log.d(
//                TAG,
//                "REQUESTING DISCOVERABLE MODE"
//            )
//
//            val intent =
//                android.content.Intent(
//                    BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE
//                )
//
//            intent.putExtra(
//                BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
//                300
//            )
//
//            intent.addFlags(
//                android.content.Intent.FLAG_ACTIVITY_NEW_TASK
//            )
//
//            context.startActivity(intent)
//
//        } catch (e: Exception) {
//
//            Log.e(
//                TAG,
//                "DISCOVERABLE REQUEST FAILED",
//                e
//            )
//        }
//    }
//
//
//    // ============================================================
//    // GET PAIRED DEVICES
//    // ============================================================
//
//    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
//    fun getPairedDevices(): List<BluetoothDevice> {
//
//        if (
//            !hasPermission(
//                Manifest.permission.BLUETOOTH_CONNECT
//            )
//        ) {
//
//            return emptyList()
//        }
//
//        return try {
//
//            bluetoothAdapter
//                ?.bondedDevices
//                ?.toList()
//                ?: emptyList()
//
//        } catch (e: Exception) {
//
//            Log.e(
//                TAG,
//                "FAILED TO GET PAIRED DEVICES",
//                e
//            )
//
//            emptyList()
//        }
//    }
//
//
//    // ============================================================
//    // JOIN MESH
//    // ============================================================
//
//    fun joinMesh(
//        device: BluetoothDevice
//    ) {
//
//        if (
//            !hasPermission(
//                Manifest.permission.BLUETOOTH_CONNECT
//            )
//        ) {
//
//            Log.e(
//                TAG,
//                "BLUETOOTH_CONNECT PERMISSION MISSING"
//            )
//
//            return
//        }
//
//        if (meshStarted) {
//
//            Log.d(
//                TAG,
//                "MESH ALREADY RUNNING"
//            )
//
//            return
//        }
//
//        meshStarted = true
//        joinedDevice = device
//        startHeartbeat()
//
//        Log.d(TAG, "====================================")
//        Log.d(TAG, "JOINING RESQMESH NETWORK")
//        Log.d(TAG, "LOCAL DEVICE = $localDeviceName")
//        Log.d(TAG, "TARGET DEVICE = ${safeDeviceName(device)}")
//        Log.d(TAG, "TARGET ADDRESS = ${device.address}")
//        Log.d(TAG, "ROLE = JOIN")
//        Log.d(TAG, "====================================")
//
//        Thread {
//
//            var socket: BluetoothSocket? = null
//
//            try {
//
//                Log.d(
//                    TAG,
//                    "CREATING RFCOMM CONNECTION..."
//                )
//
//                socket =
//                    device.createInsecureRfcommSocketToServiceRecord(
//                        SERVICE_UUID
//                    )
//
//                socket.connect()
//
//                Log.d(TAG, "====================================")
//                Log.d(
//                    TAG,
//                    "RESQMESH CONNECTION SUCCESS"
//                )
//
//                Log.d(
//                    TAG,
//                    "CONNECTED TO = ${safeDeviceName(device)}"
//                )
//
//                Log.d(
//                    TAG,
//                    "ADDRESS = ${device.address}"
//                )
//
//                Log.d(TAG, "====================================")
//
//                registerConnection(socket)
//
//            } catch (e: Exception) {
//
//                Log.e(
//                    TAG,
//                    "RESQMESH JOIN FAILED",
//                    e
//                )
//
//                try {
//                    socket?.close()
//                } catch (_: Exception) {
//                }
//
//                meshStarted = false
//                joinedDevice = null
//                stopHeartbeat()
//            }
//
//        }.start()
//    }
//
//
//    // ============================================================
//    // REGISTER CONNECTION
//    // ============================================================
//
//    private fun registerConnection(
//        socket: BluetoothSocket
//    ) {
//
//        if (
//            !hasPermission(
//                Manifest.permission.BLUETOOTH_CONNECT
//            )
//        ) {
//            return
//        }
//
//        val device =
//            socket.remoteDevice
//
//        val address =
//            device.address
//
//        val oldSocket =
//            connectedSockets.putIfAbsent(
//                address,
//                socket
//            )
//
//        if (oldSocket != null) {
//
//            Log.d(
//                TAG,
//                "CONNECTION ALREADY EXISTS -> $address"
//            )
//
//            try {
//                socket.close()
//            } catch (_: Exception) {
//            }
//
//            return
//        }
//
//        */
///*
//         * Create ONE permanent writer for this socket.
//         *//*
//
//        try {
//
//            val writer =
//                PrintWriter(
//                    OutputStreamWriter(
//                        socket.outputStream
//                    ),
//                    true
//                )
//
//            connectedWriters[address] =
//                writer
//
//        } catch (e: Exception) {
//
//            Log.e(
//                TAG,
//                "WRITER CREATION FAILED",
//                e
//            )
//
//            connectedSockets.remove(address)
//
//            try {
//                socket.close()
//            } catch (_: Exception) {
//            }
//
//            return
//        }
//
//        Log.d(TAG, "====================================")
//
//        Log.d(
//            TAG,
//            "RESQMESH NODE CONNECTED"
//        )
//
//        Log.d(
//            TAG,
//            "REMOTE = ${safeDeviceName(device)}"
//        )
//
//        Log.d(
//            TAG,
//            "ADDRESS = $address"
//        )
//
//        Log.d(
//            TAG,
//            "CONNECTED NODES = ${connectedSockets.size}"
//        )
//
//        Log.d(TAG, "====================================")
//
//        onDeviceCountChanged?.invoke(
//            connectedSockets.size
//        )
//
//        */
///*
//         * Start receiving messages.
//         *//*
//
//        startReader(
//            socket,
//            address
//        )
//
//        */
///*
//         * Once connected, send any messages
//         * that were waiting locally.
//         *//*
//
//        sendPendingMessagesToNode(address)
//    }
//
//
//    // ============================================================
//    // READ MESSAGES
//    // ============================================================
//
//    private fun startReader(
//        socket: BluetoothSocket,
//        address: String
//    ) {
//
//        Thread {
//
//            try {
//
//                val reader =
//                    BufferedReader(
//                        InputStreamReader(
//                            socket.inputStream
//                        )
//                    )
//
//                while (meshStarted) {
//
//                    val message =
//                        reader.readLine()
//                            ?: break
//
//                    if (message.isBlank()) {
//                        continue
//                    }
//
//                    Log.d(
//                        TAG,
//                        "===================================="
//                    )
//
//                    Log.d(
//                        TAG,
//                        "MESSAGE RECEIVED FROM = $address"
//                    )
//
//                    Log.d(
//                        TAG,
//                        message
//                    )
//
//                    Log.d(
//                        TAG,
//                        "===================================="
//                    )
//
//                    processReceivedMessage(
//                        message,
//                        address
//                    )
//                }
//
//            } catch (e: Exception) {
//
//                Log.e(
//                    TAG,
//                    "READ ERROR FROM $address : ${e.message}"
//                )
//
//            } finally {
//
//                removeConnection(address, socket)
//            }
//
//        }.start()
//    }
//
//
//    // ============================================================
//    // REMOVE CONNECTION
//    // ============================================================
//
//    private fun removeConnection(
//        address: String,
//        socket: BluetoothSocket? = null
//    ) {
//
//        */
///*
//         * A reader belonging to an OLD socket must never remove a newer
//         * connection that has already been established for the same device.
//         *//*
//
//        val currentSocket = connectedSockets[address]
//
//        if (
//            socket != null &&
//            currentSocket != null &&
//            currentSocket !== socket
//        ) {
//            Log.d(
//                TAG,
//                "IGNORING OLD SOCKET DISCONNECT -> $address"
//            )
//            return
//        }
//
//        connectedWriters.remove(address)
//
//        val removedSocket =
//            connectedSockets.remove(address)
//
//        try {
//            removedSocket?.close()
//        } catch (_: Exception) {
//        }
//
//        onDeviceCountChanged?.invoke(
//            connectedSockets.size
//        )
//
//        Log.d(
//            TAG,
//            "NODE DISCONNECTED -> $address"
//        )
//
//        Log.d(
//            TAG,
//            "CONNECTED NODES = ${connectedSockets.size}"
//        )
//
//        */
///*
//         * If this phone is the JOINER, automatically try to restore the
//         * connection. The HOST continues listening, so no user action is
//         * required after a temporary RFCOMM drop.
//         *//*
//
//        if (
//            meshStarted &&
//            joinedDevice != null
//        ) {
//            scheduleReconnect()
//        }
//    }
//
//
//    // ============================================================
//    // AUTOMATIC RECONNECT
//    // ============================================================
//
//    private fun scheduleReconnect() {
//
//        if (reconnectScheduled) {
//            return
//        }
//
//        val device = joinedDevice ?: return
//
//        reconnectScheduled = true
//
//        reconnectRunnable = Runnable {
//
//            reconnectScheduled = false
//
//            if (!meshStarted) {
//                return@Runnable
//            }
//
//            if (connectedSockets.isNotEmpty()) {
//                return@Runnable
//            }
//
//            if (
//                !hasPermission(
//                    Manifest.permission.BLUETOOTH_CONNECT
//                )
//            ) {
//                Log.e(
//                    TAG,
//                    "RECONNECT SKIPPED - BLUETOOTH_CONNECT PERMISSION MISSING"
//                )
//                return@Runnable
//            }
//
//            Log.d(
//                TAG,
//                "===================================="
//            )
//
//            Log.d(
//                TAG,
//                "RESQMESH AUTOMATIC RECONNECT"
//            )
//
//            Log.d(
//                TAG,
//                "TARGET = ${safeDeviceName(device)}"
//            )
//
//            Log.d(
//                TAG,
//                "ADDRESS = ${device.address}"
//            )
//
//            Log.d(
//                TAG,
//                "===================================="
//            )
//
//            Thread {
//
//                var socket: BluetoothSocket? = null
//
//                try {
//
//                    socket =
//                        device.createInsecureRfcommSocketToServiceRecord(
//                            SERVICE_UUID
//                        )
//
//                    socket.connect()
//
//                    if (!meshStarted) {
//                        try {
//                            socket.close()
//                        } catch (_: Exception) {
//                        }
//                        return@Thread
//                    }
//
//                    Log.d(
//                        TAG,
//                        "RESQMESH AUTOMATIC RECONNECT SUCCESS"
//                    )
//
//                    registerConnection(socket)
//
//                } catch (e: Exception) {
//
//                    Log.e(
//                        TAG,
//                        "AUTOMATIC RECONNECT FAILED: ${e.message}"
//                    )
//
//                    try {
//                        socket?.close()
//                    } catch (_: Exception) {
//                    }
//
//                    if (meshStarted && joinedDevice != null) {
//                        reconnectScheduled = false
//                        scheduleReconnect()
//                    }
//                }
//
//            }.start()
//        }
//
//        heartbeatHandler.postDelayed(
//            reconnectRunnable!!,
//            RECONNECT_DELAY_MS
//        )
//    }
//
//
//    // ============================================================
//    // CONNECTION HEARTBEAT
//    // ============================================================
//
//    private fun startHeartbeat() {
//
//        if (heartbeatRunnable != null) {
//            return
//        }
//
//        heartbeatRunnable = object : Runnable {
//
//            override fun run() {
//
//                if (!meshStarted) {
//                    return
//                }
//
//                sendHeartbeat()
//
//                heartbeatHandler.postDelayed(
//                    this,
//                    HEARTBEAT_INTERVAL_MS
//                )
//            }
//        }
//
//        heartbeatHandler.postDelayed(
//            heartbeatRunnable!!,
//            HEARTBEAT_INTERVAL_MS
//        )
//    }
//
//
//    private fun stopHeartbeat() {
//
//        heartbeatRunnable?.let {
//            heartbeatHandler.removeCallbacks(it)
//        }
//
//        heartbeatRunnable = null
//    }
//
//
//    private fun sendHeartbeat() {
//
//        if (connectedWriters.isEmpty()) {
//            return
//        }
//
//        val heartbeat =
//            JSONObject().apply {
//                put(
//                    "messageType",
//                    "MESH_HEARTBEAT"
//                )
//                put(
//                    "timestamp",
//                    System.currentTimeMillis()
//                )
//            }.toString()
//
//        connectedWriters.forEach { (address, writer) ->
//
//            Thread {
//
//                try {
//
//                    synchronized(writer) {
//                        writer.println(heartbeat)
//                        writer.flush()
//                    }
//
//                    Log.d(
//                        TAG,
//                        "HEARTBEAT SENT -> $address"
//                    )
//
//                } catch (e: Exception) {
//
//                    Log.e(
//                        TAG,
//                        "HEARTBEAT FAILED -> $address : ${e.message}"
//                    )
//
//                    removeConnection(address)
//                }
//            }.start()
//        }
//    }
//
//
//    // ============================================================
//    // GENERIC MESSAGE SEND
//    // ============================================================
//
//    fun sendMessage(
//        message: String
//    ) {
//
//        sendMessage(
//            message = message,
//            excludeAddress = null
//        )
//    }
//
//
//    private fun sendMessage(
//        message: String,
//        excludeAddress: String?
//    ) {
//
//        if (connectedSockets.isEmpty()) {
//
//            Log.w(
//                TAG,
//                "NO RESQMESH NODES CONNECTED"
//            )
//
//            return
//        }
//
//        connectedWriters.forEach { (address, writer) ->
//
//            */
///*
//             * Never immediately send a received message
//             * back to the node that sent it.
//             *//*
//
//            if (
//                excludeAddress != null &&
//                address == excludeAddress
//            ) {
//                return@forEach
//            }
//
//            Thread {
//
//                try {
//
//                    synchronized(writer) {
//
//                        writer.println(message)
//
//                        writer.flush()
//                    }
//
//                    Log.d(
//                        TAG,
//                        "MESSAGE SENT -> $address"
//                    )
//
//                } catch (e: Exception) {
//
//                    Log.e(
//                        TAG,
//                        "SEND FAILED -> $address",
//                        e
//                    )
//
//                    removeConnection(address)
//                }
//
//            }.start()
//        }
//    }
//
//
//    // ============================================================
//    // PUBLIC TEXT MESSAGE API
//    // ============================================================
//
//    fun sendTextMessage(
//        message: String
//    ) {
//
//        if (message.isBlank()) {
//
//            Log.w(
//                TAG,
//                "EMPTY MESSAGE IGNORED"
//            )
//
//            return
//        }
//
//        val meshMessage =
//            MeshMessage(
//                messageId = UUID.randomUUID().toString(),
//                senderDeviceId = localDeviceId,
//                message = message,
//                timestamp = System.currentTimeMillis(),
//                hopCount = 0,
//                ttl = DEFAULT_TTL
//            )
//
//        */
///*
//         * Save locally first.
//         *//*
//
//        CoroutineScope(
//            Dispatchers.IO
//        ).launch {
//
//            val entity =
//                MeshMessageEntity(
//                    messageId =
//                        meshMessage.messageId,
//
//                    senderDeviceId =
//                        meshMessage.senderDeviceId,
//
//                    message =
//                        meshMessage.message,
//
//                    timestamp =
//                        meshMessage.timestamp,
//
//                    hopCount =
//                        meshMessage.hopCount,
//
//                    ttl =
//                        meshMessage.ttl,
//
//                    transmitted =
//                        false
//                )
//
//            database
//                .meshMessageDao()
//                .insertMessage(entity)
//
//            */
///*
//             * Then transmit.
//             *//*
//
//            withContext(
//                Dispatchers.Main
//            ) {
//
//                val json =
//                    meshMessageToJson(
//                        meshMessage
//                    )
//
//                Log.d(
//                    TAG,
//                    "===================================="
//                )
//
//                Log.d(
//                    TAG,
//                    "SENDING MESH TEXT MESSAGE"
//                )
//
//                Log.d(
//                    TAG,
//                    "MESSAGE ID = ${meshMessage.messageId}"
//                )
//
//                Log.d(
//                    TAG,
//                    "MESSAGE = ${meshMessage.message}"
//                )
//
//                Log.d(
//                    TAG,
//                    "CONNECTED NODES = ${connectedSockets.size}"
//                )
//
//                Log.d(
//                    TAG,
//                    "===================================="
//                )
//
//                sendMessage(
//                    json.toString()
//                )
//            }
//        }
//    }
//
//
//    // ============================================================
//    // CONVERT MESH MESSAGE -> JSON
//    // ============================================================
//
//    private fun meshMessageToJson(
//        message: MeshMessage
//    ): JSONObject {
//
//        return JSONObject().apply {
//
//            put(
//                "messageType",
//                MESSAGE_TYPE_TEXT
//            )
//
//            put(
//                "messageId",
//                message.messageId
//            )
//
//            put(
//                "senderDeviceId",
//                message.senderDeviceId
//            )
//
//            put(
//                "message",
//                message.message
//            )
//
//            put(
//                "timestamp",
//                message.timestamp
//            )
//
//            put(
//                "hopCount",
//                message.hopCount
//            )
//
//            put(
//                "ttl",
//                message.ttl
//            )
//        }
//    }
//
//
//    // ============================================================
//    // PROCESS RECEIVED MESSAGE
//    // ============================================================
//
//    private fun processReceivedMessage(
//        rawMessage: String,
//        sourceAddress: String
//    ) {
//
//        try {
//
//            val json =
//                JSONObject(rawMessage)
//
//            val messageType =
//                json.optString(
//                    "messageType",
//                    "UNKNOWN"
//                )
//
//            */
///*
//             * Heartbeat is only a connection-keepalive packet.
//             * Never expose it to the application UI or relay it.
//             *//*
//
//            if (messageType == "MESH_HEARTBEAT") {
//                Log.d(TAG, "HEARTBEAT RECEIVED FROM = $sourceAddress")
//                return
//            }
//
//            */
///*
//             * ====================================================
//             * NORMAL MESH TEXT MESSAGE
//             * ====================================================
//             *//*
//
//
//            if (
//                messageType == MESSAGE_TYPE_TEXT
//            ) {
//
//                processMeshTextMessage(
//                    json,
//                    sourceAddress
//                )
//
//                return
//            }
//
//            */
///*
//             * ====================================================
//             * SOS MESSAGE
//             * ====================================================
//             *//*
//
//
//            if (
//                messageType == MESSAGE_TYPE_SOS ||
//                messageType == MESSAGE_TYPE_PERSON_IN_DANGER
//            ) {
//
//                processEmergencyMessage(
//                    json,
//                    sourceAddress
//                )
//
//                return
//            }
//
//            */
///*
//             * Unknown message type.
//             *//*
//
//            Handler(
//                Looper.getMainLooper()
//            ).post {
//
//                onMessageReceived?.invoke(
//                    rawMessage
//                )
//            }
//
//        } catch (e: Exception) {
//
//            Log.e(
//                TAG,
//                "MESSAGE PROCESSING FAILED",
//                e
//            )
//        }
//    }
//
//
//    // ============================================================
//    // PROCESS NORMAL MESH TEXT
//    // ============================================================
//
//    private fun processMeshTextMessage(
//        json: JSONObject,
//        sourceAddress: String
//    ) {
//
//        val messageId =
//            json.optString(
//                "messageId"
//            )
//
//        if (messageId.isBlank()) {
//
//            Log.w(
//                TAG,
//                "MESSAGE WITHOUT ID IGNORED"
//            )
//
//            return
//        }
//
//        val senderDeviceId =
//            json.optString(
//                "senderDeviceId",
//                "UNKNOWN"
//            )
//
//        val message =
//            json.optString(
//                "message",
//                ""
//            )
//
//        val timestamp =
//            json.optLong(
//                "timestamp",
//                System.currentTimeMillis()
//            )
//
//        val hopCount =
//            json.optInt(
//                "hopCount",
//                0
//            )
//
//        val ttl =
//            json.optInt(
//                "ttl",
//                DEFAULT_TTL
//            )
//
//        CoroutineScope(
//            Dispatchers.IO
//        ).launch {
//
//            */
///*
//             * ====================================================
//             * DUPLICATE PROTECTION
//             * ====================================================
//             *//*
//
//
//            val existing =
//                database
//                    .meshMessageDao()
//                    .getMessage(
//                        messageId
//                    )
//
//            if (existing != null) {
//
//                Log.d(
//                    TAG,
//                    "DUPLICATE MESSAGE IGNORED -> $messageId"
//                )
//
//                return@launch
//            }
//
//            */
///*
//             * ====================================================
//             * SAVE RECEIVED MESSAGE
//             * ====================================================
//             *//*
//
//
//            val entity =
//                MeshMessageEntity(
//                    messageId =
//                        messageId,
//
//                    senderDeviceId =
//                        senderDeviceId,
//
//                    message =
//                        message,
//
//                    timestamp =
//                        timestamp,
//
//                    hopCount =
//                        hopCount,
//
//                    ttl =
//                        ttl,
//
//                    transmitted =
//                        false
//                )
//
//            database
//                .meshMessageDao()
//                .insertMessage(
//                    entity
//                )
//
//            */
///*
//             * ====================================================
//             * SHOW MESSAGE TO UI
//             * ====================================================
//             *//*
//
//
//            withContext(
//                Dispatchers.Main
//            ) {
//
//                onMessageReceived?.invoke(
//                    message
//                )
//            }
//
//            */
///*
//             * ====================================================
//             * MULTI-HOP RELAY
//             * ====================================================
//             *
//             * Example:
//             *
//             * A -> B -> C -> D
//             *
//             * TTL prevents infinite forwarding.
//             *//*
//
//
//            if (ttl > 0) {
//
//                val relay =
//                    JSONObject().apply {
//
//                        put(
//                            "messageType",
//                            MESSAGE_TYPE_TEXT
//                        )
//
//                        put(
//                            "messageId",
//                            messageId
//                        )
//
//                        put(
//                            "senderDeviceId",
//                            senderDeviceId
//                        )
//
//                        put(
//                            "message",
//                            message
//                        )
//
//                        put(
//                            "timestamp",
//                            timestamp
//                        )
//
//                        put(
//                            "hopCount",
//                            hopCount + 1
//                        )
//
//                        put(
//                            "ttl",
//                            ttl - 1
//                        )
//                    }
//
//                sendMessage(
//                    message =
//                        relay.toString(),
//                    excludeAddress =
//                        sourceAddress
//                )
//
//                database
//                    .meshMessageDao()
//                    .markAsTransmitted(
//                        messageId
//                    )
//
//                Log.d(
//                    TAG,
//                    "MESH MESSAGE RELAYED -> $messageId"
//                )
//            }
//        }
//    }
//
//
//    // ============================================================
//    // SEND PENDING MESSAGES TO NEW NODE
//    // ============================================================
//
//    private fun sendPendingMessagesToNode(
//        address: String
//    ) {
//
//        CoroutineScope(
//            Dispatchers.IO
//        ).launch {
//
//            val pending =
//                database
//                    .meshMessageDao()
//                    .getPendingMessages()
//
//            if (pending.isEmpty()) {
//
//                Log.d(
//                    TAG,
//                    "NO PENDING MESH MESSAGES"
//                )
//
//                return@launch
//            }
//
//            val writer =
//                connectedWriters[address]
//
//            if (writer == null) {
//
//                Log.w(
//                    TAG,
//                    "NO WRITER FOR NODE -> $address"
//                )
//
//                return@launch
//            }
//
//            pending.forEach { entity ->
//
//                try {
//
//                    val json =
//                        JSONObject().apply {
//
//                            put(
//                                "messageType",
//                                MESSAGE_TYPE_TEXT
//                            )
//
//                            put(
//                                "messageId",
//                                entity.messageId
//                            )
//
//                            put(
//                                "senderDeviceId",
//                                entity.senderDeviceId
//                            )
//
//                            put(
//                                "message",
//                                entity.message
//                            )
//
//                            put(
//                                "timestamp",
//                                entity.timestamp
//                            )
//
//                            put(
//                                "hopCount",
//                                entity.hopCount
//                            )
//
//                            put(
//                                "ttl",
//                                entity.ttl
//                            )
//                        }
//
//                    synchronized(writer) {
//
//                        writer.println(
//                            json.toString()
//                        )
//
//                        writer.flush()
//                    }
//
//                    database
//                        .meshMessageDao()
//                        .markAsTransmitted(
//                            entity.messageId
//                        )
//
//                    Log.d(
//                        TAG,
//                        "PENDING MESSAGE SENT -> ${entity.messageId}"
//                    )
//
//                } catch (e: Exception) {
//
//                    Log.e(
//                        TAG,
//                        "PENDING MESSAGE SEND FAILED",
//                        e
//                    )
//                }
//            }
//        }
//    }
//
//
//    // ============================================================
//    // SEND SOS
//    // ============================================================
//
//    fun sendEmergencyPacket(
//        packetId: String,
//        senderDeviceId: String,
//        messageType: String,
//        latitude: Double?,
//        longitude: Double?,
//        severity: String,
//        message: String,
//        timestamp: Long,
//        hopCount: Int,
//        ttl: Int
//    ) {
//
//        val json =
//            JSONObject().apply {
//
//                put(
//                    "packetId",
//                    packetId
//                )
//
//                put(
//                    "senderDeviceId",
//                    senderDeviceId
//                )
//
//                put(
//                    "messageType",
//                    messageType
//                )
//
//                put(
//                    "latitude",
//                    latitude ?: JSONObject.NULL
//                )
//
//                put(
//                    "longitude",
//                    longitude ?: JSONObject.NULL
//                )
//
//                put(
//                    "severity",
//                    severity
//                )
//
//                put(
//                    "message",
//                    message
//                )
//
//                put(
//                    "timestamp",
//                    timestamp
//                )
//
//                put(
//                    "hopCount",
//                    hopCount
//                )
//
//                put(
//                    "ttl",
//                    ttl
//                )
//            }
//
//        Log.d(
//            TAG,
//            "===================================="
//        )
//
//        Log.d(
//            TAG,
//            "SENDING SOS PACKET = $packetId"
//        )
//
//        Log.d(
//            TAG,
//            "CONNECTED NODES = ${connectedSockets.size}"
//        )
//
//        Log.d(
//            TAG,
//            "===================================="
//        )
//
//        sendMessage(
//            json.toString()
//        )
//    }
//
//
//    // ============================================================
//    // PROCESS SOS
//    // ============================================================
//
//    private fun processEmergencyMessage(
//        json: JSONObject,
//        sourceAddress: String
//    ) {
//
//        val packetId =
//            json.optString(
//                "packetId"
//            )
//
//        if (packetId.isBlank()) {
//            return
//        }
//
//        val senderDeviceId =
//            json.optString(
//                "senderDeviceId",
//                "UNKNOWN"
//            )
//
//        val latitude =
//            if (
//                json.isNull("latitude")
//            ) {
//                null
//            } else {
//                json.optDouble("latitude")
//            }
//
//        val longitude =
//            if (
//                json.isNull("longitude")
//            ) {
//                null
//            } else {
//                json.optDouble("longitude")
//            }
//
//        val severity =
//            json.optString(
//                "severity",
//                "UNKNOWN"
//            )
//
//        val emergencyMessage =
//            json.optString(
//                "message",
//                "Emergency reported"
//            )
//
//        val messageType =
//            json.optString(
//                "messageType",
//                MESSAGE_TYPE_SOS
//            )
//
//        val timestamp =
//            json.optLong(
//                "timestamp",
//                System.currentTimeMillis()
//            )
//
//        val hopCount =
//            json.optInt(
//                "hopCount",
//                0
//            )
//
//        val ttl =
//            json.optInt(
//                "ttl",
//                DEFAULT_TTL
//            )
//
//        CoroutineScope(
//            Dispatchers.IO
//        ).launch {
//
//            val existing =
//                database
//                    .emergencyPacketDao()
//                    .getPacket(
//                        packetId
//                    )
//
//            if (existing != null) {
//
//                Log.d(
//                    TAG,
//                    "DUPLICATE SOS IGNORED -> $packetId"
//                )
//
//                return@launch
//            }
//
//            val packet =
//                EmergencyPacketEntity(
//
//                    packetId =
//                        packetId,
//
//                    senderDeviceId =
//                        senderDeviceId,
//
//                    messageType =
//                        messageType,
//
//                    latitude =
//                        latitude,
//
//                    longitude =
//                        longitude,
//
//                    severity =
//                        severity,
//
//                    message =
//                        emergencyMessage,
//
//                    timestamp =
//                        timestamp,
//
//                    hopCount =
//                        hopCount,
//
//                    ttl =
//                        ttl,
//
//                    transmitted =
//                        false
//                )
//
//            database
//                .emergencyPacketDao()
//                .insertPacket(
//                    packet
//                )
//
//            withContext(
//                Dispatchers.Main
//            ) {
//
//                onEmergencyPacketReceived?.invoke(
//                    packet
//                )
//            }
//
//            */
///*
//             * Relay SOS to other nodes.
//             *//*
//
//            if (ttl > 0) {
//
//                val relay =
//                    JSONObject().apply {
//
//                        put(
//                            "packetId",
//                            packetId
//                        )
//
//                        put(
//                            "senderDeviceId",
//                            senderDeviceId
//                        )
//
//                        put(
//                            "messageType",
//                            messageType
//                        )
//
//                        put(
//                            "latitude",
//                            latitude
//                                ?: JSONObject.NULL
//                        )
//
//                        put(
//                            "longitude",
//                            longitude
//                                ?: JSONObject.NULL
//                        )
//
//                        put(
//                            "severity",
//                            severity
//                        )
//
//                        put(
//                            "message",
//                            emergencyMessage
//                        )
//
//                        put(
//                            "timestamp",
//                            timestamp
//                        )
//
//                        put(
//                            "hopCount",
//                            hopCount + 1
//                        )
//
//                        put(
//                            "ttl",
//                            ttl - 1
//                        )
//                    }
//
//                sendMessage(
//                    message =
//                        relay.toString(),
//                    excludeAddress =
//                        sourceAddress
//                )
//
//                Log.d(
//                    TAG,
//                    "SOS RELAYED -> $packetId"
//                )
//            }
//        }
//    }
//
//
//    // ============================================================
//    // STOP MESH
//    // ============================================================
//
//    @Synchronized
//    fun stopMesh() {
//
//        Log.d(
//            TAG,
//            "STOPPING RESQMESH"
//        )
//
//        meshStarted = false
//        joinedDevice = null
//
//        reconnectRunnable?.let {
//            heartbeatHandler.removeCallbacks(it)
//        }
//        reconnectRunnable = null
//        reconnectScheduled = false
//
//        stopHeartbeat()
//
//        try {
//
//            serverSocket?.close()
//
//        } catch (_: Exception) {
//        }
//
//        serverSocket = null
//
//        serverThread?.interrupt()
//
//        serverThread = null
//
//        connectedWriters.forEach { (_, writer) ->
//
//            try {
//                writer.close()
//            } catch (_: Exception) {
//            }
//        }
//
//        connectedWriters.clear()
//
//        connectedSockets.forEach { (_, socket) ->
//
//            try {
//                socket.close()
//            } catch (_: Exception) {
//            }
//        }
//
//        connectedSockets.clear()
//
//        onDeviceCountChanged?.invoke(
//            0
//        )
//
//        Log.d(
//            TAG,
//            "RESQMESH STOPPED"
//        )
//    }
//
//
//    // ============================================================
//    // DEVICE NAME
//    // ============================================================
//
//    private fun safeDeviceName(
//        device: BluetoothDevice
//    ): String {
//
//        return try {
//
//            device.name
//                ?: "Unknown Device"
//
//        } catch (
//            e: SecurityException
//        ) {
//
//            "Unknown Device"
//        }
//    }
//
//
//    // ============================================================
//    // PERMISSION
//    // ============================================================
//
//    private fun hasPermission(
//        permission: String
//    ): Boolean {
//
//        return ContextCompat.checkSelfPermission(
//            context,
//            permission
//        ) ==
//                PackageManager.PERMISSION_GRANTED
//    }
//}
//
//*/
