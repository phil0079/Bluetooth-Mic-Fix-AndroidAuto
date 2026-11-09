# Android and shell routing boundaries

Reviewed 2026-11-09. These findings do not establish the physical microphone or a working fix.

## Ordinary app

`AudioRecord.setPreferredDevice()` selects input for that recorder; it does not configure another app's recorder. `AudioTrack` provides a separate preference for its own playback. Their actual behavior on this handset remains to be acoustically tested. [AudioRecord reference](https://developer.android.com/reference/android/media/AudioRecord#setPreferredDevice(android.media.AudioDeviceInfo)).

`setCommunicationDevice()` accepts an output; Android chooses the corresponding input. Competing requests prioritize the audio-mode owner. Ordinary app access does not provide the main telephony app's `MODE_IN_CALL` authority. Thus the accepted repair request in the earlier car test proves neither microphone selection nor authority over the call. [AudioManager reference](https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo)).

Two ordinary recording apps generally cannot both receive audio concurrently; Android can silence one. Running Mic Lab alongside a third-party recorder is therefore not a neutral way to inspect that recorder's microphone. Stop capture before other-app tests. The lab records its own client-silencing flag. [Audio-input sharing policy](https://developer.android.com/media/platform/sharing-audio-input).

## Actual Samsung shell findings

Read-only ADB observations on SM-S948U1/API 37:

- `com.android.shell` reports granted `MODIFY_AUDIO_ROUTING`, `MODIFY_AUDIO_SETTINGS_PRIVILEGED`, `MODIFY_AUDIO_SETTINGS`, `MODIFY_PHONE_STATE`, `CAPTURE_AUDIO_OUTPUT`, and `DUMP`. These are existing platform grants; we did not grant them, record calls, or change security settings.
- `dumpsys media.audio_policy` succeeds and contains available input/output device sections. Only capability flags were returned to the chat; raw content was neither saved nor uploaded.
- The audio service is listed. Both `cmd audio help` and `cmd audio -h` returned exit code zero with no output. This does **not** establish support for any routing command. The upstream mutating `set-force-use` command was not tried.

Shizuku launched through ADB executes as shell UID 2000, subject to actual permissions and other checks. Therefore it is incorrect to rule out additional routing access merely because Shizuku is not root. Package permission grants also do not prove that every Binder call or hardware route will work. [Shizuku developer guide](https://github.com/RikkaApps/Shizuku-API#differents-of-the-privilege-betweent-adb-and-root).

AOSP has privileged capture-preset preference APIs, including `getPreferredDevicesForCapturePreset`, `setPreferredDevicesForCapturePreset`, and `clearPreferredDevicesForCapturePreset`. The service enforces `MODIFY_AUDIO_ROUTING`. These refer to recording source categories, not necessarily a cellular call's modem input path. They are hidden APIs, and AOSP main is not proof of Samsung's implementation. [Service implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/audio/AudioService.java).

## Read-only probe

`ShellAudioProbe` is packaged in Mic Lab but is not an Android component and is not invoked by its UI. Explicit ADB `app_process` execution as UID 2000 queries mode and capture-preset preferences for MIC (1), VOICE_RECOGNITION (6), and VOICE_COMMUNICATION (7). It has a ten-second watchdog, no arbitrary command arguments, no recording, and no mutation methods. It reports only device role/type, never names or addresses. It does not disable hidden-API enforcement or modify security settings.

Build and execution validated on 2026-11-09: shell UID 2000, reported mode 0, preferredCount=0 for all three presets. This establishes diagnostic access only. An empty result is ambiguous: AOSP returns an empty list for a native query failure as well as no preferences. The probe cannot prove microphone use or control over another app.

## Scope for any later experiment

First finish the public-API acoustic baseline. A capture-preset change would be broader than the standing authorization for **app-scoped** changes and needs separate authorization before use. Such an experiment would require capturing/restoring preexisting preferences, a bounded lifetime and verified cleanup; it must not run during calls or driving. No global setter is implemented in this build. Shizuku integration is deferred until it adds a demonstrated benefit over the already-authorized ADB diagnostics.

The Android Auto/Samsung/Telecom root cause and RAV4 fallback behavior remain unverified. Permissions, source review and reported state are not acoustic success.
