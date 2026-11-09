# Next desk test session

Status: paused at the user's request on 2026-11-09. No further phone actions, tones, captures, builds or installations until the user resumes. Current test processes finished before the pause; audio mode was observed back at NORMAL with no mode owner. Do not treat this plan as a new setup confirmation.

## Findings to retain

- BT Mic Lab 0.2.2 (`95e4180a56af`) is installed. Cloud build, unit tests and lint passed. Same-key update from 0.2.1 succeeded without uninstall/data clearing, and the displayed version/startup and permissions were verified.
- Eight preliminary trials completed. User subsequently reported the door between rooms was open. At the user's subsequent request, all eight preliminary result sets were deleted from the laptop and Mic Lab, including the app's latest-result copy; deletion was verified. Only these summary observations remain. Original diagnostics-app logs and settings were untouched. New trials start with no existing Mic Lab baseline file; adapt the repetition helper's initial-result check accordingly when resuming.
- Input preference alone returned accepted while Android reported phone input. Three paired communication trials reported Shokz input. Three paired-plus-playback trials reported Shokz input and phone-speaker output simultaneously. These remain reported-route observations, not physical input/output proof.
- Pure 997 Hz sound produced brief onset responses followed by strong attenuation during reported Shokz capture. Noise suppression is a hypothesis. Tone shape and sampling cadence may need improvement before drawing acoustic conclusions.
- Read-only shell API probe succeeded under UID 2000: audio mode 0; preferred input lists for MIC, VOICE_RECOGNITION and VOICE_COMMUNICATION were empty. Empty lists are ambiguous, and read access proves no routing fix.
- The supplied Cloudflare URL `https://amber-otter.pages.dev/` was verified to serve **Work Tracker**, not this Bluetooth project. No deployment was performed. Identify the correct project before any deployment.

## Resume gate and preparation

The user explicitly resumes and confirms they are at the desk, not driving, with no active call. Revalidate the ADB endpoint, app version, permissions, headset connection and idle audio state. Inspect the existing app/results before changing anything. Preserve both project apps and all unique results.

Before captures, agree a quiet physical setup and confirm laptop playback uses the physical speaker. Keep headset multipoint state, boom orientation, speaker level and source placement constant. Do not raise volume merely to defeat noise suppression.

The agent runs all trials within a setup without separate approvals. A physical move or disconnection still requires one user instruction and readiness confirmation at a time. No tests while the user moves the equipment.

## Two placement blocks

**A — headset near sound:** Shokz boom near the laptop speaker; phone in the other quiet room; door closed. User confirms ready. Run the matrix below.

**B — reverse control:** phone near the same laptop speaker; Shokz in the other quiet room; door closed. User confirms the swap is complete. Repeat the same matrix without changing speaker level or stimulus. Recheck headset connection after the move.

| Condition | Recorder preference | Paired communication request | App playback |
|---|---|---|---|
| 1: phone reference | Primary phone mic | Off | None |
| 2: input preference alone | Shokz mic | Off | None |
| 3: voice connection | Shokz mic | On, released afterward | None |
| 4: separate playback | Shokz mic | On, released afterward | Quiet phone-speaker marker |

Run three rounds of conditions 1–4 in each placement (24 captures total), checking teardown between trials. Interleave conditions rather than completing all repeats of one condition first. Distinguish cold Bluetooth voice-connection startup from a still-active transport left by an earlier trial. Start the next baseline only once teardown is confirmed. Use unique labels recording placement, condition and round; never overwrite a result.

## Measurements and evidence gates

- Record requested input, actual reported input, output and communication route; silencing state; duration/sample count; clipping; signal levels; start/stop timing and cleanup.
- Calibrate the stimulus first. If a steady tone is suppressed, prepare a bounded varying/burst tone stimulus and measure it at sufficient cadence. Keep the first protocol's results separate; do not silently change stimuli midway through a comparison.
- Determine physical microphone from the **same routing condition across placements**, timed sound-versus-silence contrast, and repeatability. Different microphones can have different gain/noise suppression, so absolute loudness between microphone types is insufficient.
- Aim for a clear placement-dependent contrast (roughly 15 dB when appropriate), consistent over three trials. Treat that as an investigation criterion, not a universal automatic pass threshold. If separation is weak, change one physical variable or stimulus and rerun affected controls; record inconclusive otherwise.
- Verify physical playback separately. In placement B the user can confirm the quiet low marker actually comes from the phone speaker. Android's output label alone is insufficient. If needed, schedule one short marker-only listening check instead of overlapping tones.
- Claim repeatable own-app input/output independence only if the acoustic microphone evidence and physical output check agree with the reported routes through repeated starts and cleanup. Make no system-wide claim.

## Follow-up scenarios after the matrix

1. **Headset absent:** after one user-confirmed disconnection, run three phone-mic baseline trials. Confirm the lab leaves normal routing untouched and offers no stale headset route. This is a desk fallback check, not proof of RAV4 behavior.
2. **Reconnect/reopen:** after reconnection confirmation, repeat the successful condition three times, including an app close/reopen, to rule out a one-off warmed-up connection.
3. **Cleanup/cancellation:** one bounded test stopped early; one interrupted by leaving the lab. Confirm capture, playback, mode and app-owned route requests end; keep the partial result labeled cancelled.
4. **Other apps:** only after the own-app result is clear. Plan a separate controlled recording test without sending anything. Mic Lab capture must stop first because concurrent recording changes Android's input arbitration. Do not infer that another app inherits our recorder's preference.
5. **Cellular/Android Auto:** deferred. Calls still need separate authorization and safe parked conditions. Compare headset-only, car Bluetooth without projection if available, Android Auto with selected headset, and headset-absent car fallback. A desk result cannot certify these cases.

## Deliverables

Keep raw audio discarded and numeric results local. Publish only a concise, nonidentifying aggregate result table and clear limitations; no sensitive logs. Update AI-HANDOFF.md with installed build, protocol versions, evidence, uncertain cases and next action. Preserve the persistent signing key outside Git. Deliver the signed APK; publish downloads only to a verified Bluetooth-project destination.

Resume phrase: **“Resume the desk test plan in docs/NEXT-TEST-SESSION.md. I’m at my desk with the phone and Shokz.”** Ask for the first physical setup, then run its full batch autonomously after confirmation.
