# Android Auto microphone investigation

Status: diagnostics only; no verified routing fix or established root cause.

## First build

Install **BT Mic Diagnostics**, package `com.btmicfix.diagnostics`, from the
`bt-mic-diagnostics-debug` artifact on the successful **Diagnostics debug APK**
GitHub Actions run. Sign in to GitHub, download/extract the ZIP, and open
`diagnostics-debug.apk` on Android. A debug APK is not a Play Store release.
CI uses an ephemeral debug signing key; a future build may require uninstalling
this diagnostics app first (which deletes its local session). Preserve any needed
results before doing that. The original BTMicFix app is a separate package.

The new module is isolated from upstream routing code. It requests Bluetooth
connection access, foreground-service permissions, and optional notifications.
It requests no microphone, call-log, phone-state, contacts, location, scanning,
internet, or storage permission. There is no Shizuku component in this APK.

Start a local session while the app is visible. It monitors AudioManager device,
communication-device and mode callbacks, plus Bluetooth connection/HFP audio
broadcasts and HFP/A2DP/LE Audio profile snapshots. A 2-second state poll fills
some callback gaps. CarConnection reports projection state; manual AA markers
provide a cross-check. Session monitoring continues with the dialer open and
screen off using a connected-device foreground service. Android may still kill
the process; it does not automatically restart. Check RUNNING status and the
notification before each test. Missing callbacks are not proof of no change.

The report is capped at 400 events and saved in app-private `last-session.txt`.
Starting a new session replaces it. Names are displayed only in the live device
legend; names, addresses, phone numbers, call content and raw system dumps are
never written to the report. Aliases reset each session. Audio IDs are temporary;
devices whose addresses Android hides cannot reliably be matched across APIs.
No upload, automatic sharing, clipboard export, or call recording is performed.
The in-app report remains readable after stopping. Stop with the app button or
notification. Do not force-stop if you want a clean SESSION STOP marker.

## What the source inspection established

Upstream at `d38e6ccfb05d83ede27d99c68a4d2506073b9bd7` is Apache-2.0, with
Android Gradle Plugin 8.7.3, Gradle 8.9, Java 17, compile/target SDK 34, min SDK 31.
Its Kotlin 1.9.24 plugin was paired with Compose compiler plugin 2.0.0. This fork
aligns Kotlin to 2.0.0. CI builds/tests/lints only the new module; this is not a
validation of the original app.

`AudioRoutingManager` sets MODE_IN_COMMUNICATION and calls
setCommunicationDevice, labels a true return Active, and resets MODE_NORMAL on
clear/failure. It does not verify a physical microphone. The activity selects the
first available Bluetooth device on ACL connect when auto-route is enabled
(default true), and clears routing on ANY Bluetooth disconnect. The companion
service also selects the first device. These are unsuitable baseline behaviors
when a headset and car coexist. Upstream debug logs include device names and MACs.
The diagnostics APK does not include or invoke that code.

Upstream Shizuku allows string prefixes then runs `sh -c`, which is not a safe
command allowlist: shell operators can follow an allowed prefix. Its fallback
uses `cmd audio set-force-use`, and interprets output as success without checking
the resulting route. Current AOSP AudioManagerShellCommand has no such command;
unknown commands can return 0. That is not a supported telecom workaround.

## Control boundaries and Shizuku

AudioManager exposes available input/output devices, communication candidates,
reported communication output and audio mode. Available inputs do not identify
the active cellular microphone. setCommunicationDevice selects an output and
Android selects a corresponding source. A true result is not acoustic proof.
Routing arbitration gives priority to the app controlling the audio mode;
MODE_IN_CALL requires privileged telephony access. This ordinary app is not a
default dialer/InCallService and cannot use its call endpoint control APIs.
Becoming the default dialer entails a real replacement phone UI and emergency
call responsibilities, far beyond this minimal diagnostic build.

Shizuku started through ADB runs with shell privileges, not unrestricted system
or root privileges. It may help inspect mode ownership, audio-policy devices,
and Bluetooth SCO state through supported dumpsys commands. Actual visibility
and permission failures must be checked on the Samsung build. Raw `dumpsys audio`,
`media.audio_policy`, `bluetooth_manager`, or `telecom` output can contain sensitive
data. Do not upload them or integrate a generic shell runner. A later optional
helper should execute fixed argument arrays with time/output limits and return
only allowlisted structured fields. It must never claim Shizuku bypasses Telecom.
No Shizuku or ADB commands have been run on the phone for this implementation.

CarConnection + routing timelines can show correlation with Android Auto
connection. Public callbacks do not name the caller that changed the route.
They cannot alone attribute the problem to Android Auto, Samsung, Telecom, the
headset, or its multipoint behavior. A handset-distance effect is evidence to
test, not conclusive proof of the selected physical microphone.

## Proposed preference policy (not enabled in this build)

| Situation | Desired behavior |
|---|---|
| Headset connected AND user selects it for this call | Prefer the same headset's input/output pair through the system call UI; observe actual outcome |
| Headset disconnected | Leave normal car/Telecom selection intact; never force speaker/phone or reset mode |
| No car connection | Leave normal Android Bluetooth behavior intact |
| Selection unknown, multiple devices, active cellular call | Observe; no first-device fallback or repeated route forcing |

Future implementation should store explicitly selected headset/car identities
privately, separate user preference from reported route and acoustic evidence,
and never infer call selection from ACL or A2DP connection. Only clear a route
request owned by this app, not a system call route. A VoIP-only experiment, if
needed, must be explicit, bounded, and separate from cellular testing. Cellular
override remains unimplemented unless a supported mechanism is demonstrated.

## Parked, phone-only test plan

1. Disable any other routing-fix app for the baseline. Keep headset multipoint
   state, headset position, phone position, call partner and volume constant.
   Start one diagnostics session; grant notification permission if desired.
   Note the headset/car aliases from the live legend. Keep this session for the
   whole comparison so aliases remain consistent.
2. **A — headset only, no car/AA.** Mark AA OFF and Call starting. Make an ordinary
   cellular call, select the headset in the dialer's audio selector, return to
   diagnostics and mark Headset selected. Take a snapshot during the call.
3. **B — car Bluetooth connected, AA off**, if the car allows this independently.
   Repeat A without changing other variables. This separates a second HFP device
   from Android Auto projection. If impossible, record B as skipped.
4. **C — AA on, headset connected and selected.** Confirm projection is actually
   visible on the car, mark AA ON, and repeat. Record wired vs wireless AA.
5. **D — AA on, headset disconnected.** Confirm the car is selected for calls;
   repeat to check that normal RAV4 microphone behavior remains available.
6. For each call, ask the listener to compare the same phrase at a normal speaking
   level. Keep your head/boom position fixed, first move ONLY the phone closer
   and farther away, then restore it. If the headset has hardware mic mute, test
   it separately and note whether it mutes the whole call (which is inconclusive).
   Compare near-car-mic speech separately. Use multiple trials; loudness/noise
   suppression can mislead. Record headset/phone/car strongest or inconclusive,
   plus reported communication device, HFP audio state, mode, and AA state.
7. Mark Call ended, repeat A after C to check reversibility, then Stop session.
   Review locally. Share only a short result table you choose to type; no raw
   diagnostic dumps, identifying screenshots or recordings are needed.

If the route says headset but the listener consistently detects the phone,
record that disagreement as the finding. Do not relabel reported state as proof.
All UI manipulation and test calls should happen while parked.

## Sources

- [AudioManager API and routing priority](https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo))
- [InCallService and default-dialer requirements](https://developer.android.com/reference/android/telecom/InCallService)
- [CarConnection API](https://developer.android.com/training/cars/apps/library/connection-api)
- [Shizuku limitations](https://shizuku.rikka.app/guide/setup/)
- [AOSP audio shell commands](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/audio/AudioManagerShellCommand.java)
- [Kotlin/Compose compiler alignment](https://kotlinlang.org/docs/compose-compiler-migration-guide.html)

## Attribution

Fork of [Endda/BTMicFix](https://github.com/Endda/BTMicFix).
Original LICENSE and source attribution are retained. New diagnostic source is
also Apache-2.0. The original README follows the fork notice in the root README.
