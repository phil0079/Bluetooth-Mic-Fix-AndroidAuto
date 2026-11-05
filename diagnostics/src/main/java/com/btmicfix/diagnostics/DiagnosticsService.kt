// Copyright 2026 BTMicFix Contributors. SPDX-License-Identifier: Apache-2.0
package com.btmicfix.diagnostics

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import java.io.File

/** Passive observer: never opens audio, requests focus, or changes a route/mode. */
class DiagnosticsService : Service() {
    companion object {
        var current: DiagnosticsService? = null
            private set
        const val FILE_NAME = "last-session.txt"
        const val STOP = "com.btmicfix.diagnostics.STOP"
    }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private var adapter: BluetoothAdapter? = null
    private val profiles = mutableMapOf<Int, BluetoothProfile>()
    private val requested = mutableSetOf<Int>()
    private val journal = EventJournal()
    private var started = 0L
    private var lastSnapshot = ""
    private var carState = "unknown"
    private var car: CarConnection? = null
    private var running = false
    var report = ""
        private set
    var deviceLegend = ""
        private set

    private val carObserver = Observer<Int> { value ->
        carState = when (value) {
            CarConnection.CONNECTION_TYPE_PROJECTION -> "projection reported (Android Auto)"
            CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> "not connected reported"
            CarConnection.CONNECTION_TYPE_NATIVE -> "native automotive reported"
            else -> "unknown"
        }
        capture("car connection callback")
    }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>) { capture("audio devices added") }
        override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>) { capture("audio devices removed") }
    }
    private val routeListener = AudioManager.OnCommunicationDeviceChangedListener { capture("communication route callback") }
    private val modeListener = AudioManager.OnModeChangedListener { capture("audio mode callback") }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Never stringify the intent, extras, device name, or address.
            val event = when (intent.action) {
                BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> "HFP audio state broadcast"
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> "HFP connection broadcast"
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> "A2DP connection broadcast"
                BluetoothAdapter.ACTION_STATE_CHANGED -> "Bluetooth adapter broadcast"
                else -> "Bluetooth link broadcast"
            }
            capture(event, true)
        }
    }
    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (!running) { adapter?.closeProfileProxy(profile, proxy); return }
            profiles[profile] = proxy
            capture("profile proxy ready")
        }
        override fun onServiceDisconnected(profile: Int) {
            profiles.remove(profile)
            requested.remove(profile)
            capture("profile proxy unavailable", true)
        }
    }
    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            capture("state changed (2-second sample)")
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        adapter = getSystemService(BluetoothManager::class.java)?.adapter
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY
        }
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("diagnostics", "Local diagnostics", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, DiagnosticsActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, DiagnosticsService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        startForeground(2, Notification.Builder(this, "diagnostics")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("BT diagnostics observing locally")
            .setContentText("No recording or routing changes. Tap to view.")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build())
        running = true
        current = this
        started = SystemClock.elapsedRealtime()
        record("SESSION START; API ${Build.VERSION.SDK_INT}; physical microphone UNVERIFIED")
        audio.registerAudioDeviceCallback(deviceCallback, handler)
        audio.addOnCommunicationDeviceChangedListener(mainExecutor, routeListener)
        audio.addOnModeChangedListener(mainExecutor, modeListener)
        val filter = IntentFilter().apply {
            addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        // Bluetooth broadcasts can originate from the privileged Bluetooth app.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        else registerReceiver(receiver, filter)
        car = CarConnection(this).also { it.type.observeForever(carObserver) }
        handler.post(poll)
        return START_NOT_STICKY
    }

    private fun record(event: String) {
        journal.add((SystemClock.elapsedRealtime() - started) / 1000, event)
        report = "Reported system state only; NOT verified physical microphone use.\n" +
            "Names/addresses omitted. Aliases are session-local; audio IDs can change.\n" + journal.text()
        // Bounded app-private file; no external storage, network, or automatic export.
        try { File(filesDir, FILE_NAME).writeText(report) }
        catch (_: java.io.IOException) { report += "\nLOCAL SAVE FAILED; keep app open to view." }
    }

    fun marker(label: String) {
        // Caller supplies only fixed UI labels, never free-form private information.
        if (running) { record("USER MARKER: $label"); capture("marker snapshot", true) }
    }

    @SuppressLint("MissingPermission")
    fun capture(reason: String = "manual snapshot", force: Boolean = false) {
        if (!running) return
        val legend = linkedSetOf<String>()
        fun describe(device: AudioDeviceInfo): String {
            val key = if (device.address.isNullOrBlank()) "audio:${device.id}" else "bt:${device.address}"
            val alias = journal.alias(key)
            legend.add("$alias = ${device.productName} (audio id ${device.id})")
            return "$alias id=${device.id} type=${typeName(device.type)} input=${device.isSource} output=${device.isSink}"
        }
        fun btAlias(device: BluetoothDevice): String {
            val alias = journal.alias("bt:${device.address}")
            legend.add("$alias = ${device.name ?: "unnamed Bluetooth device"}")
            return alias
        }
        fun safe(block: () -> String): String = try { block() }
            catch (_: SecurityException) { "unavailable (permission denied)" }
            catch (_: RuntimeException) { "unavailable (system API failure)" }
        val snapshot = buildString {
            appendLine("Audio mode=${safe { modeName(audio.mode) }}; muted=${safe { audio.isMicrophoneMute.toString() }}")
            appendLine("Communication output=${safe { audio.communicationDevice?.let(::describe) ?: "none reported" }}")
            appendLine("Available inputs (NOT active microphones):\n${safe { audio.getDevices(AudioManager.GET_DEVICES_INPUTS).joinToString("\n", transform = ::describe) }}")
            appendLine("Available outputs:\n${safe { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).joinToString("\n", transform = ::describe) }}")
            appendLine("Communication candidates:\n${safe { audio.availableCommunicationDevices.joinToString("\n", transform = ::describe) }}")
            appendLine("CarConnection=$carState; correlation does not prove who changed routing")
            appendLine("Bluetooth enabled=${safe { adapter?.isEnabled?.toString() ?: "no adapter" }}")
            val ids = mutableListOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP)
            if (Build.VERSION.SDK_INT >= 33) ids.add(BluetoothProfile.LE_AUDIO)
            for (id in ids) {
                val name = when (id) { BluetoothProfile.HEADSET -> "HFP"; BluetoothProfile.A2DP -> "A2DP"; else -> "LE Audio" }
                appendLine("$name: " + safe {
                    if (adapter?.isEnabled == true && id !in requested) {
                        if (adapter?.getProfileProxy(this@DiagnosticsService, profileListener, id) == true) requested.add(id)
                    }
                    val proxy = profiles[id]
                    if (proxy == null) "unknown (proxy unavailable/pending)"
                    else proxy.connectedDevices.joinToString { device ->
                        btAlias(device) + " connected" + if (proxy is BluetoothHeadset) " HFP-audio=${proxy.isAudioConnected(device)}" else ""
                    }.ifEmpty { "no connected devices reported" }
                })
            }
        }
        deviceLegend = legend.joinToString("\n")
        if (force || snapshot != lastSnapshot) { lastSnapshot = snapshot; record("$reason\n$snapshot") }
    }

    override fun onDestroy() {
        if (running) {
            running = false
            handler.removeCallbacksAndMessages(null)
            audio.unregisterAudioDeviceCallback(deviceCallback)
            audio.removeOnCommunicationDeviceChangedListener(routeListener)
            audio.removeOnModeChangedListener(modeListener)
            unregisterReceiver(receiver)
            car?.type?.removeObserver(carObserver)
            profiles.forEach { (id, proxy) -> adapter?.closeProfileProxy(id, proxy) }
            profiles.clear()
            record("SESSION STOP; no route or audio mode was modified")
        }
        current = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

private fun modeName(mode: Int) = when (mode) {
    AudioManager.MODE_NORMAL -> "NORMAL"
    AudioManager.MODE_RINGTONE -> "RINGTONE"
    AudioManager.MODE_IN_CALL -> "IN_CALL (not a physical mic measurement)"
    AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
    else -> "mode-$mode"
}
private fun typeName(type: Int) = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC"
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "EARPIECE"
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT_SCO"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BT_A2DP"
    AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
    else -> "type-$type"
}
