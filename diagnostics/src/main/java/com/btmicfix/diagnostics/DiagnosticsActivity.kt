// Copyright 2026 BTMicFix Contributors. SPDX-License-Identifier: Apache-2.0
package com.btmicfix.diagnostics

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.io.File

class DiagnosticsActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var report: TextView
    private lateinit var legend: TextView
    private lateinit var repairStatus: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var snapshotButton: Button
    private lateinit var repairButton: Button
    private lateinit var diagnosticsPanel: LinearLayout
    private lateinit var repairPanel: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val update = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 1500) }
    }

    private fun text(parent: LinearLayout, value: String, size: Float = 16f): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setPadding(0, 10, 0, 10)
            parent.addView(this)
        }

    private fun button(parent: LinearLayout, label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
            parent.addView(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        text(root, "Bluetooth Mic Fix", 24f)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(tabs)
        repairPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        diagnosticsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val repairTab = button(tabs, "Repair") { showTab(false) }
        val diagnosticsTab = button(tabs, "Diagnostics") { showTab(true) }
        repairTab.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        diagnosticsTab.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        root.addView(repairPanel)
        root.addView(diagnosticsPanel)

        text(repairPanel, "Experimental routing request. Android may refuse or override this during cellular calls. A successful request does NOT verify the physical microphone.", 14f)
        repairStatus = text(repairPanel, "No repair attempted.")
        repairButton = button(repairPanel, "Request Bluetooth headset route") { requestRepair() }
        text(repairPanel, "This button makes one request only. It never changes audio mode, repeatedly forces routing, or automatically selects the car.", 14f)

        status = text(diagnosticsPanel, "")
        button(diagnosticsPanel, "Allow Bluetooth observation") {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 10)
        }
        if (Build.VERSION.SDK_INT >= 33) button(diagnosticsPanel, "Allow notification (optional)") {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
        startButton = button(diagnosticsPanel, "Start local session") {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Allow Bluetooth observation first", Toast.LENGTH_LONG).show()
            else try { startForegroundService(Intent(this, DiagnosticsService::class.java)) }
            catch (_: RuntimeException) { Toast.makeText(this, "Could not start session", Toast.LENGTH_LONG).show() }
            handler.postDelayed({ render() }, 400)
        }
        stopButton = button(diagnosticsPanel, "Stop session") {
            stopService(Intent(this, DiagnosticsService::class.java))
            handler.postDelayed({ render() }, 400)
        }
        snapshotButton = button(diagnosticsPanel, "Capture current state (text log)") {
            DiagnosticsService.current?.capture(force = true)
            render()
        }
        text(diagnosticsPanel, "Capture current state appends a timestamped text entry to the local log. It does not take a screenshot.", 14f)
        button(diagnosticsPanel, "Share log to ChatGPT or save file") { shareLog() }
        text(diagnosticsPanel, "The Android share menu lets you choose ChatGPT, Downloads via a file manager, or another app. Nothing uploads automatically.", 14f)
        text(diagnosticsPanel, "Markers are your observations, not automatic detection.", 14f)
        for (marker in listOf("AA OFF observed", "AA ON observed", "Call starting", "Call ended",
            "Headset selected in dialer", "Car selected in dialer", "Listener: headset strongest",
            "Listener: phone strongest", "Listener: car strongest", "Listener: inconclusive")) {
            button(diagnosticsPanel, marker) { DiagnosticsService.current?.marker(marker); render() }
        }
        legend = text(diagnosticsPanel, "")
        text(diagnosticsPanel, "Local text report (last 400 events). Starting a new session replaces the previous report.", 14f)
        report = text(diagnosticsPanel, "", 13f).apply { setTextIsSelectable(true) }
        setContentView(ScrollView(this).apply { addView(root) })
        showTab(false)
        render()
    }

    private fun showTab(diagnostics: Boolean) {
        repairPanel.visibility = if (diagnostics) android.view.View.GONE else android.view.View.VISIBLE
        diagnosticsPanel.visibility = if (diagnostics) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun requestRepair() {
        val audio = getSystemService(AudioManager::class.java)
        val devices = audio.availableCommunicationDevices.filter {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
        if (devices.isEmpty()) {
            repairStatus.text = "No Bluetooth communication candidates available."
            return
        }
        // Explicit selection prevents accidentally choosing the car.
        val labels = devices.map { "${it.productName} (device ${it.id})" }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Choose headset for this routing request")
            .setItems(labels) { _, index -> attemptRoute(audio, devices[index]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun attemptRoute(audio: AudioManager, candidate: AudioDeviceInfo) {
        // Never change audio mode or reset another app's route.
        val accepted = try { audio.setCommunicationDevice(candidate) }
        catch (e: SecurityException) {
            repairStatus.text = "Routing request denied by Android."
            return
        } catch (e: RuntimeException) {
            repairStatus.text = "Routing request failed: Android system rejected the operation."
            return
        }
        val reported = audio.communicationDevice?.id == candidate.id
        repairStatus.text = "Request accepted=$accepted; reported output matches=$reported. Physical microphone NOT verified. Cellular calling may override this."
        DiagnosticsService.current?.marker("Manual routing experiment; accepted=$accepted; reportedMatch=$reported")
    }

    private fun shareLog() {
        val file = File(filesDir, DiagnosticsService.FILE_NAME)
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(this, "No log yet. Start a session first.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            // Export only the already-sanitized report, through a temporary private cache copy.
            val outDir = File(cacheDir, "shared").apply { mkdirs() }
            val export = File(outDir, "bluetooth-mic-diagnostics.txt")
            export.writeText(file.readText())
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", export)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newUri(contentResolver, "Diagnostics log", uri)
            }
            startActivity(Intent.createChooser(send, "Share diagnostic text log"))
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to share log", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() { super.onStart(); handler.post(update) }
    override fun onStop() { handler.removeCallbacksAndMessages(null); super.onStop() }
    private fun render() {
        val service = DiagnosticsService.current
        val running = service != null
        status.text = if (running) "SESSION RUNNING" else "SESSION STOPPED (last saved report below)"
        startButton.isEnabled = !running
        stopButton.isEnabled = running
        snapshotButton.isEnabled = running
        legend.text = service?.deviceLegend ?: "Device names unavailable when session is stopped."
        val value = service?.report ?: try {
            File(filesDir, DiagnosticsService.FILE_NAME).takeIf { it.exists() }?.readText() ?: "No session yet."
        } catch (_: java.io.IOException) { "Could not read local report." }
        if (report.text.toString() != value) report.text = value
    }
}
