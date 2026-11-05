// Copyright 2026 BTMicFix Contributors. SPDX-License-Identifier: Apache-2.0
package com.btmicfix.diagnostics

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.widget.*
import androidx.activity.ComponentActivity
import java.io.File

class DiagnosticsActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var report: TextView
    private lateinit var legend: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val update = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 1500) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        fun text(value: String): TextView = TextView(this).apply {
            text = value; textSize = 16f; setPadding(0, 10, 0, 10); column.addView(this)
        }
        fun button(label: String, action: () -> Unit) {
            column.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        text("BT Mic Diagnostics · 0.1").textSize = 24f
        text("Use while parked. Passive observation only: no recording, route changes, or uploads. " +
            "The communication device is a reported OUTPUT; the actual call microphone remains UNVERIFIED.")
        status = text("")
        button("1. Allow Bluetooth observation") {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 10)
        }
        if (Build.VERSION.SDK_INT >= 33) button("Allow session notification (optional)") {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
        button("2. Start local session") {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Allow Bluetooth observation first", Toast.LENGTH_LONG).show()
            } else {
                try { startForegroundService(Intent(this, DiagnosticsService::class.java)) }
                catch (_: RuntimeException) { Toast.makeText(this, "Could not start session; reopen app and retry", Toast.LENGTH_LONG).show() }
            }
        }
        button("Stop session") { stopService(Intent(this, DiagnosticsService::class.java)); handler.postDelayed({ render() }, 300) }
        button("Snapshot now") { DiagnosticsService.current?.capture(force = true); render() }
        text("Markers are your observations, not automatic detection. Mark before/after each parked test.")
        for (marker in listOf("AA OFF observed", "AA ON observed", "Call starting", "Call ended", "Headset selected in dialer", "Car selected in dialer", "Listener: headset strongest", "Listener: phone strongest", "Listener: car strongest", "Listener: inconclusive")) {
            button(marker) { DiagnosticsService.current?.marker(marker); render() }
        }
        text("Device names below are shown only on this screen, never saved. Match the headset/car to their session aliases. " +
            "Audio and Bluetooth aliases may differ when Android hides an address.")
        legend = text("")
        text("Local report · newest state is at the bottom · last 400 events. Starting a new session replaces the previous saved report. " +
            "A missing STOP marker can mean Android killed the process. No automatic restart; check the session status.")
        report = text("").apply { setTextIsSelectable(true); textSize = 13f }
        setContentView(ScrollView(this).apply { addView(column) })
    }
    override fun onStart() { super.onStart(); handler.post(update) }
    override fun onStop() { handler.removeCallbacksAndMessages(null); super.onStop() }
    private fun render() {
        val service = DiagnosticsService.current
        status.text = if (service != null) "SESSION RUNNING — continues with dialer open / screen off (subject to Android process limits)" else "SESSION STOPPED — showing last saved report, which may be incomplete"
        legend.text = service?.deviceLegend ?: "Names unavailable after session stops; saved report uses aliases only."
        val value = service?.report ?: try { File(filesDir, DiagnosticsService.FILE_NAME).takeIf { it.exists() }?.readText() ?: "No session yet." }
            catch (_: java.io.IOException) { "Could not read local report." }
        if (report.text.toString() != value) report.text = value
    }
}
