# Phone troubleshooting handoff

Continue Bluetooth-Mic-Fix-AndroidAuto from this verified Codex handoff, dated November 5, 2026. I am switching to phone-only troubleshooting; give one short step at a time and have me do setup, route changes and microphone comparisons only while parked.

Codex created the fork and draft PR https://github.com/phil0079/Bluetooth-Mic-Fix-AndroidAuto/pull/1 on branch diagnostics-first. Read the source audit, limitations, preference design and parked test plan at https://github.com/phil0079/Bluetooth-Mic-Fix-AndroidAuto/blob/diagnostics-first/docs/DIAGNOSTICS.md

GitHub Actions compilation, unit tests and Android lint passed at commit 48b644848ae5b0f7e93fcc09668eabb714edc0f4. APK artifact: https://github.com/phil0079/Bluetooth-Mic-Fix-AndroidAuto/actions/runs/42131807849/artifacts/13463417633

Codex installed BT Mic Diagnostics 0.1 on my USB-connected S26 Ultra and verified that its main screen opens. Package: com.btmicfix.diagnostics. Bluetooth observation permission, optional notifications and Start local session still need my action. No session has been started, no calls placed, and no physical microphone test performed. Background survival on this phone remains unverified.

The new app is passive and separate from the original BTMicFix. It observes available inputs/outputs, reported communication output, audio mode, HFP audio and HFP/A2DP/LE Audio connections, and Android Auto projection state. A foreground session is designed to continue with the dialer open or screen off. It saves only the last 400 events locally, with session aliases instead of names/addresses. Starting another session replaces the previous report. Names appear only in the live legend. There are manual AA, call, dialer-selection and listener-result markers. No recordings, uploads, Shizuku, root or routing changes. Do not enable the original BTMicFix auto-routing during baseline tests.

We have NOT established the root cause or a routing fix. Moving the phone closer is suggestive, not proof. Android reported routing is NOT verified physical microphone use. AudioManager is not a general cellular-call override; Telecom restrictions remain. The original Shizuku shell fallback is unreliable and excluded. Preserve normal RAV4 microphone behavior when the headset is absent.

First help me open BT Mic Diagnostics, allow Bluetooth and optionally notifications, start a session and confirm RUNNING. Then while parked compare A: headset only with AA/car disconnected; B: car Bluetooth with AA off if possible; C: AA on and headset selected in the dialer; D: AA on with headset disconnected and car selected. Repeat A to check reversibility. Keep phone/headset position and call partner constant. Compare the same phrase while moving only the phone, then restore it; distinguish listener evidence from system state. Record only a brief non-sensitive results table. Do not request recordings or raw dumps. Ask whether I am parked before any interactive test. No computer should be needed.

