# Controlled microphone tests

The `miclab` module installs as **BT Mic Lab**, package `com.btmicfix.miclab`, alongside the existing diagnostics app. It requests microphone, Bluetooth observation, and audio-settings permissions. No internet permission, background capture, raw audio files, or telemetry. Each foreground test runs for at most eight seconds after optional two-second Bluetooth transport setup; leaving the screen cancels it. It releases its own route/mode requests afterward. Do not test during calls or driving.

## Evidence and boundaries

- `AudioRecord.setPreferredDevice()` requests input for **our recorder only**. Accepted preference and reported routed device are not acoustic proof.
- `AudioTrack.setPreferredDevice()` requests playback for **our track only**. The optional quiet 440 Hz phone-speaker marker tests simultaneous playback preference; it is not a global output selector.
- `AudioManager.setCommunicationDevice()` requests a communication **output**; Android selects its corresponding input. The optional paired Bluetooth experiment can affect communication routing while active, so it is off by default and cleared afterward.
- Success here does not establish control over Samsung Voice Recorder, Marco Polo, or cellular calls. Failure in another app does not prove failure in ours, and vice versa.
- The app logs Android's reported input/output, communication device, client-silencing state, RMS level and a 997 Hz frequency measurement in dBFS. Results omit device names/addresses and are stored privately under `files/results/<unique-id>.txt`. `latest-test.txt` is a convenience copy; unique results are preserved.
- Unit tests verify the level estimator with synthetic signals, silence, gain changes, and an off-frequency tone. Those tests do not validate microphone hardware.

API references: [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord), [AudioTrack](https://developer.android.com/reference/android/media/AudioTrack), [AudioManager.setCommunicationDevice](https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo)).

## Acoustic protocol (not yet executed)

Only start after the user confirms the physical setup. Give one setup instruction at a time.

1. Connected Shokz beside the laptop's physical speaker, phone isolated in another room. No active calls or other microphone apps. Confirm laptop sound uses its speaker, not Bluetooth.
2. Play a moderate laptop 997 Hz pattern with silent intervals. Capture with phone input, then Bluetooth input; both optional checkboxes off. Compare the tone's timing and level, actual reported route, and silencing status. Save numerical results locally.
3. If Bluetooth is ignored or capture fails, test the separate paired communication checkbox, documenting that input/output may then be coupled.
4. Repeat each condition at least three times. Swap the physical sound placement for a reverse control before identifying the physical microphone. Reject ambiguous results, dropouts, clipping, or background contamination.
5. Only after microphone identification, enable the quiet phone-speaker playback marker and repeat. Report the actual playback route separately; verify physical playback as well before claiming independent routing.

Treat a repeatable coded-tone difference (aim for at least 15 dB over the isolated control, with quiet-period contrast) as evidence, not an automatic pass threshold. Automatic gain control, leakage, and noise reduction can confound levels. No raw recordings are needed for this first protocol. The 997 Hz metric is computed on short windows; align elapsed time and pattern before comparing.

## Build and signing

Workflow `Desk microphone lab` compiles, runs estimator tests and lint in GitHub Actions. Its artifact is **unsigned** and cannot be installed directly. It also packages the Android SDK's official `apksigner.jar`. The authorized laptop signs with a persistent private test key stored outside the repository; never commit or upload the key/password. Install with `adb install -r` and verify the package version/build and certificate. Preserve the key for future updates. Do not uninstall or clear either app to solve a signing mismatch without separate approval.

Current status: implementation prepared; cloud build, installation and acoustic validation pending. No routing success claimed.
