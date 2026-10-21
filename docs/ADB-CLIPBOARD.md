# Automatic clipboard syncing with Gboard: ADB / Shizuku investigation

Investigated and experimentally checked on 21 October 2026. **An ADB-started helper is technically viable. It is not integrated into the v0.2.1 APK's automatic sending path.**

## What was actually observed

On the dedicated Android 14/API 34 arm64 emulator, a separate Java process was launched by `adb shell app_process` as **UID 2000 (shell), not root**. The normal AOSP Latin keyboard stayed selected, another app remained the resumed/focused activity, and LocalClip's main Activity was in the background.

The helper registered a real `ClipboardManager.OnPrimaryClipChangedListener`. After synthetic text copies made by that other app, it automatically received callbacks and read the text. After an image copy, it obtained the image URI through the clipboard API and read the complete 107-byte PNG through the private source provider. No Share action or keyboard switch was needed for these probe events.

Observed diagnostic output (lengths only; no clipboard contents):

```
PROBE_CONTEXT package=com.android.shell attribution=com.android.shell uid=2000
READY shell clipboard listener; copy synthetic test text/image in another app
EVENT 1 text-readable chars=13
EVENT 2 text-readable chars=13
EVENT 3 image-readable bytes=107
```

The fixture caused two text events; this is not a throughput/latency benchmark. The helper only observes and reads; it does **not** send to the Mac or receive production pairing keys.

## Why this works

Android's [clipboard service](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/clipboard/ClipboardService.java) explicitly allows callers holding `READ_CLIPBOARD_IN_BACKGROUND`, before applying ordinary focus restrictions. The [shell package](https://android.googlesource.com/platform/frameworks/base/+/android16-release/packages/Shell/AndroidManifest.xml) requests that permission; it was also checked as granted on the emulator. In Android 16's [permission declaration](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/res/AndroidManifest.xml), the protection level is `signature|role`. It is not a normal user-grantable clipboard runtime permission.

Running an ordinary APK foreground service does not give it the shell's identity. Changing a clipboard app-op alone does not remove the clipboard service's focus/privileged-permission gate. This APK still cannot read arbitrary background copies while Gboard is selected; the privileged helper is a separate requirement.

The observed approach is also consistent with the shell-side clipboard listener in [scrcpy's clipboard wrapper](https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/wrappers/ClipboardManager.java) and its [shell context implementation](https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/FakeContext.java). No scrcpy code or runtime dependency was bundled.

## Important implementation findings

A shell process is not a normal Android application process. A naïvely created context reported an `android` attribution instead of `com.android.shell`; that prevented useful background callbacks. Correct attribution to the process's actual shell UID/package enabled the callbacks.

Images needed a separate check. The normal ContentResolver path first failed package attribution, then failed because ActivityManager could not find a registered application thread for the shell process. The tested probe instead uses the explicit external-content-provider API and shell attribution. Exact API 34 Binder method signatures were inspected on the running emulator before implementing the calls. The provider reference is released after the file is read.

The probe fails fast outside API 34 rather than guessing hidden API signatures. A Pixel running another Android release needs its actual interfaces and behavior validated. Text and images were proved on this emulator; Pixel 11 Pro XL, Android 15/16/17 runtime, screen lock, Doze, process longevity, multi-user profiles and reboot recovery were not tested.

## How this could become automatic LocalClip sync

There are two plausible deployment routes:

1. **An own-source ADB-started helper.** Launch it using an explicitly authorized USB/wireless-debugging connection, then feed clipboard events into a carefully authenticated local IPC channel to the APK. The APK can retain the pairing key and existing encrypted Mac transport. Add visible helper status, stop/revoke controls, sensitive-content filtering, bounded image reads, and echo suppression before shipping. The experiment does not yet implement that channel or auto-send.
2. **Shizuku user service.** [Shizuku's API documentation](https://github.com/RikkaApps/Shizuku-API/blob/master/README.md) describes running user-service code as shell/root, and warns that many Context APIs do not behave like a normal app. On non-rooted devices, its backend must be started again after reboot; Android 11+ can use wireless debugging. This adds a separate open-source app/dependency and authorization flow. It was researched, not installed or tested here.

A helper must be relaunched after reboot or process termination. One setup that lasts permanently through reboot without further privileges was not established. Keeping Gboard is compatible with the helper approach because it monitors **clipboard changes**, not keyboard keystrokes.

## Reproduce only with synthetic data on an isolated API 34 emulator

The probe source and build script are in `experiments/adb-clipboard/`. It has no network code and prints no raw clipboard contents, URI paths, or hashes. It skips sensitive-marked clips and caps image reads at 8 MiB.

```sh
experiments/adb-clipboard/build-probe.sh
adb -s emulator-5554 push .build/adb-probe/clipboard-probe.jar /data/local/tmp/localclip-probe.jar
adb -s emulator-5554 shell 'CLASSPATH=/data/local/tmp/localclip-probe.jar app_process /system/bin localclip.probe.ClipboardProbe'
```

With the synthetic test fixture installed, another terminal can cause a copy in the source app:

```sh
adb -s emulator-5554 shell am start -n org.localclip.tests/localclip.SourceActivity --es text adb-auto-text
adb -s emulator-5554 shell am start -n org.localclip.tests/localclip.SourceActivity --ez copyImage true
```

The process runs in the foreground of that debugging session. Stop it with the session or kill its specific shell-process PID after the experiment. No developer settings were enabled or permissions changed on a physical phone during this work.
