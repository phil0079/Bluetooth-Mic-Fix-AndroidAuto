// Copyright 2026 BTMicFix Contributors. SPDX-License-Identifier: Apache-2.0
package com.btmicfix.miclab

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import android.widget.*
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.*

/** Bounded foreground acoustic experiment. No raw samples ever leave the read buffer. */
class MicLabActivity : Activity() {
    private lateinit var audio: AudioManager
    private lateinit var inputs: Spinner
    private lateinit var transport: CheckBox
    private lateinit var playback: CheckBox
    private lateinit var status: TextView
    private lateinit var start: Button
    private lateinit var refresh: Button
    private var devices = emptyList<AudioDeviceInfo>()
    @Volatile private var running = false
    @Volatile private var cancelled = false
    private val output = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = getSystemService(AudioManager::class.java)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        fun label(value: String) = TextView(this).apply {
            text = value; textSize = 16f; setPadding(0, 12, 0, 12); column.addView(this)
        }
        fun button(value: String, action: () -> Unit) = Button(this).apply {
            text = value; setOnClickListener { action() }; column.addView(this)
        }
        label("BT Mic Lab ${BuildConfig.VERSION_NAME}\nBuild ${BuildConfig.SOURCE_REVISION}").textSize = 22f
        label("Desk testing only. Each test captures for 8 seconds while this screen stays open. " +
            "Audio is measured and discarded; only numeric results are saved privately. No network or uploads.")
        button("Allow microphone and Bluetooth") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT), 1)
        }
        refresh = button("Refresh input devices") { refreshDevices() }
        label("Choose an input. This preference belongs only to this app. A preference can be ignored by Android.")
        inputs = Spinner(this).also { column.addView(it) }
        transport = CheckBox(this).apply {
            text = "Also request paired Bluetooth communication route (separate experiment)"
            column.addView(this)
        }
        playback = CheckBox(this).apply {
            text = "Also request phone-speaker playback: quiet 440 Hz marker"
            column.addView(this)
        }
        label("First test: leave both options OFF. Use the laptop 997 Hz tone pattern near one microphone, " +
            "with the other microphone isolated. Android-reported input is not acoustic proof.")
        start = button("Run 8-second microphone test") { beginTest() }
        button("Stop test") { cancelled = true }
        label("Leaving this screen stops capture. Test results are preserved as separate files. " +
            "No active-call test is allowed here.")
        status = label("Ready; no audio capture has started.").apply { textSize = 13f; setTextIsSelectable(true) }
        setContentView(ScrollView(this).apply { addView(column) })
        refreshDevices()
    }

    private fun refreshDevices() {
        if (running) return
        devices = audio.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        inputs.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            devices.map { "${it.productName} | ${deviceType(it.type)} | id ${it.id}" })
    }

    private fun beginTest() {
        if (running) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Microphone and Bluetooth permissions are required."
            return
        }
        if (audio.mode != AudioManager.MODE_NORMAL) {
            status.text = "Test refused: audio mode is ${audio.mode}; end calls/voice sessions first."
            return
        }
        val chosen = devices.getOrNull(inputs.selectedItemPosition) ?: run {
            status.text = "No input available. Refresh devices."; return
        }
        val pair = transport.isChecked
        val speaker = playback.isChecked
        if (pair && chosen.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO && chosen.type != AudioDeviceInfo.TYPE_BLE_HEADSET) {
            status.text = "Paired communication experiment requires a Bluetooth input."; return
        }
        running = true
        cancelled = false
        start.isEnabled = false
        refresh.isEnabled = false
        inputs.isEnabled = false
        transport.isEnabled = false
        playback.isEnabled = false
        output.clear()
        Thread({ runTest(chosen, pair, speaker) }, "mic-lab-capture").start()
    }

    private fun append(value: String) {
        output.appendLine(value)
        val snapshot = output.toString()
        runOnUiThread { if (!isDestroyed) status.text = snapshot }
    }
    private fun describe(device: AudioDeviceInfo?) = device?.let { "id=${it.id} type=${deviceType(it.type)}" } ?: "none"

    @SuppressLint("MissingPermission")
    private fun runTest(chosen: AudioDeviceInfo, pair: Boolean, speaker: Boolean) {
        val testId = UUID.randomUUID().toString()
        var recorder: AudioRecord? = null
        var player: AudioTrack? = null
        var ownsRoute = false
        var ownsMode = false
        var stage = "setup"
        try {
            append("TEST $testId build=${BuildConfig.SOURCE_REVISION} API=${Build.VERSION.SDK_INT} epochMs=${System.currentTimeMillis()}")
            append("requestedInput=${describe(chosen)} pairedCommunication=$pair phoneSpeaker=$speaker")
            append("Reported routes and digital levels only; physical microphone must be inferred from controlled sound.")
            if (pair) {
                stage = "communication-request"
                val candidates = audio.availableCommunicationDevices.filter {
                    it.type == chosen.type &&
                        (if (chosen.address.isNotBlank()) it.address == chosen.address else it.productName.toString() == chosen.productName.toString())
                }
                check(candidates.size == 1) { "Cannot uniquely match Bluetooth input to communication output" }
                check(audio.mode == AudioManager.MODE_NORMAL) { "Audio mode changed; abort" }
                audio.mode = AudioManager.MODE_IN_COMMUNICATION
                ownsMode = true
                ownsRoute = audio.setCommunicationDevice(candidates.single())
                append("communicationRequestAccepted=$ownsRoute")
                check(ownsRoute) { "Communication request rejected" }
                // Allow transport setup, but still honor cancellation and call interruption.
                repeat(20) {
                    check(!cancelled && audio.mode == AudioManager.MODE_IN_COMMUNICATION) { "Cancelled or audio mode changed" }
                    Thread.sleep(100)
                }
            }
            val rate = 48000
            stage = "recorder-initialization"
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(min > 0) { "Unsupported capture format" }
            val capture = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(max(min * 2, 19200)).build()
            recorder = capture
            check(capture.state == AudioRecord.STATE_INITIALIZED) { "Recorder initialization failed" }
            append("inputPreferenceAccepted=${capture.setPreferredDevice(chosen)}")
            if (speaker) {
                stage = "playback-initialization"
                val destination = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).singleOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?: error("No unique phone speaker output")
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(rate * 2).build()
                player = track
                append("requestedOutput=${describe(destination)} outputPreferenceAccepted=${track.setPreferredDevice(destination)}")
                val marker = ShortArray(rate) { (32767 * 0.06 * sin(2 * PI * 440 * it / rate)).toInt().toShort() }
                check(track.write(marker, 0, marker.size) == marker.size) { "Playback buffer incomplete" }
                track.setLoopPoints(0, rate, -1)
                track.setVolume(0.25f)
                track.play()
            }
            stage = "capture"
            capture.startRecording()
            check(capture.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Capture did not start" }
            val buffer = ShortArray(4800)
            val epoch = SystemClock.elapsedRealtime()
            var lastReport = -1000L
            var total = 0L
            while (!cancelled && SystemClock.elapsedRealtime() - epoch < 8000) {
                val mode = audio.mode
                check(mode == AudioManager.MODE_NORMAL || (ownsMode && mode == AudioManager.MODE_IN_COMMUNICATION)) { "Another audio mode is active; abort" }
                val count = capture.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                check(count >= 0) { "Capture read failed ($count)" }
                if (count == 0) { Thread.sleep(20); continue }
                total += count
                val now = SystemClock.elapsedRealtime() - epoch
                if (now - lastReport >= 500) {
                    val levels = SignalMetrics.measure(buffer, count, rate)
                    val config = capture.activeRecordingConfiguration
                    append(String.format(Locale.US,
                        "+%dms input=%s output=%s communication=%s rmsDbFS=%.1f tone997DbFS=%.1f clipped=%d silenced=%s",
                        now, describe(capture.routedDevice), describe(player?.routedDevice), describe(audio.communicationDevice),
                        levels.rmsDb, levels.toneDb, levels.clipped, config?.isClientSilenced?.toString() ?: "unknown"))
                    lastReport = now
                }
                buffer.fill(0) // Do not retain captured audio.
            }
            append("END cancelled=$cancelled capturedSamples=$total")
        } catch (e: Exception) {
            // Fixed error categories only: platform exception text can contain identifiers.
            append("ABORT stage=$stage category=${e.javaClass.simpleName}; inspect device state before retry")
        } finally {
            try { recorder?.stop() } catch (_: RuntimeException) { }
            try { recorder?.release() } catch (_: RuntimeException) { append("Recorder release failed") }
            try { player?.stop() } catch (_: RuntimeException) { }
            try { player?.release() } catch (_: RuntimeException) { append("Playback release failed") }
            if (ownsRoute) try { audio.clearCommunicationDevice() } catch (_: RuntimeException) { append("Route cleanup failed") }
            if (ownsMode) try { audio.mode = AudioManager.MODE_NORMAL } catch (_: RuntimeException) { append("Mode cleanup failed") }
            append("Cleanup complete; no audio saved. Current communication=${describe(audio.communicationDevice)} mode=${audio.mode}")
            try {
                val folder = File(filesDir, "results").apply { mkdirs() }
                File(folder, "$testId.txt").writeText(output.toString())
                File(filesDir, "latest-test.txt").writeText(output.toString())
            } catch (_: java.io.IOException) { append("Local result save failed") }
            running = false
            runOnUiThread {
                if (!isDestroyed) {
                    start.isEnabled = true; refresh.isEnabled = true; inputs.isEnabled = true
                    transport.isEnabled = true; playback.isEnabled = true
                }
            }
        }
    }
    override fun onStop() { cancelled = true; super.onStop() }
}

private fun deviceType(type: Int) = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "PHONE_MIC"
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "PHONE_SPEAKER"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_VOICE"
    AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLUETOOTH_LE_HEADSET"
    else -> "TYPE_$type"
}
