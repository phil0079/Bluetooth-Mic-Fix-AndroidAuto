// Copyright 2026 BTMicFix Contributors. SPDX-License-Identifier: Apache-2.0
package com.btmicfix.miclab;

import android.os.IBinder;
import android.os.Process;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/** Explicit ADB-only read probe. No routing mutations, capture, or arbitrary commands. */
public final class ShellAudioProbe {
    private ShellAudioProbe() { }

    public static void main(String[] args) {
        if (Process.myUid() != 2000 || args.length != 0) {
            System.out.println("REFUSED: requires shell UID 2000 and no arguments");
            return;
        }
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(10000); } catch (InterruptedException ignored) { return; }
            System.out.println("TIMEOUT: read-only probe exceeded 10 seconds");
            System.exit(2);
        }, "probe-time-limit");
        watchdog.setDaemon(true);
        watchdog.start();
        try {
            System.out.println("READ_ONLY uid=2000; no audio capture or routing changes");
            Class<?> manager = Class.forName("android.os.ServiceManager");
            IBinder binder = (IBinder) manager.getMethod("getService", String.class).invoke(null, "audio");
            if (binder == null) throw new IllegalStateException();
            Class<?> stub = Class.forName("android.media.IAudioService$Stub");
            Class<?> api = Class.forName("android.media.IAudioService");
            Object service = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            System.out.println("reportedAudioMode=" + api.getMethod("getMode").invoke(service));
            Method preferred = api.getMethod("getPreferredDevicesForCapturePreset", int.class);
            Class<?> attributes = Class.forName("android.media.AudioDeviceAttributes");
            Method role = attributes.getMethod("getRole");
            Method type = attributes.getMethod("getType");
            // Fixed non-call recording presets: MIC, VOICE_RECOGNITION, VOICE_COMMUNICATION.
            for (int preset : new int[] {1, 6, 7}) {
                try {
                    Object result = preferred.invoke(service, preset);
                    if (!(result instanceof List<?>)) throw new IllegalStateException();
                    List<?> devices = (List<?>) result;
                    System.out.println("capturePreset=" + preset + " preferredCount=" + devices.size());
                    for (Object device : devices) {
                        System.out.println("  role=" + role.invoke(device) + " type=" + type.invoke(device));
                    }
                } catch (ReflectiveOperationException | RuntimeException error) {
                    System.out.println("capturePreset=" + preset + " readFailure=" + category(error));
                }
            }
            System.out.println("Preferences are not active physical routes. Empty list can also reflect a native query failure.");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            System.out.println("PROBE_UNAVAILABLE category=" + category(error));
        } finally {
            watchdog.interrupt();
        }
        System.exit(0);
    }

    private static String category(Throwable error) {
        Throwable cause = error instanceof InvocationTargetException ? error.getCause() : error;
        return (cause == null ? error : cause).getClass().getSimpleName();
    }
}
