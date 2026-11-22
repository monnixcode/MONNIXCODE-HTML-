# MONNIXCODE Workspace Android

Native Kotlin Android application that provides a Termux-compatible user-space terminal without requiring a rooted device.

## What this application is

MONNIXCODE Workspace is a native Android app, not a WebView wrapper. It combines:

- a native Kotlin terminal UI;
- a native C++17/JNI pseudo-terminal layer;
- a verified ABI-specific Bootstrap runtime;
- an app-private runtime root;
- Android Storage Access Framework file selection;
- Classic, Cyberpunk, and Matrix themes.

The design follows the same broad user-space model as Termux: a shell process runs in a private prefix with `HOME`, `PREFIX`, `PATH`, temporary storage, libraries, and a PTY. It is not a replacement for Android system root.

## Non-root security model

“Root” means the root of the application runtime, not the root user or filesystem of Android.

```text
/data/data/com.monnixcode.workspace/files/monnixcode-runtime/
├── home/       Shell home directory
├── tmp/        Runtime temporary directory
└── usr/        Prefix containing shell, libraries, etc., and packages
```

The app can fully manage its own runtime directory without `su`, Magisk, bootloader changes, or a rooted phone. Android still enforces the application sandbox:

- other applications’ private data remains inaccessible;
- `/system` and protected Android paths are not writable;
- SELinux and vendor kernel policies remain active;
- shared storage is accessed through user-selected Android document URIs;
- the app cannot grant itself system privileges.

This is intentional and is what makes the design suitable for non-root devices.

## Native PTY architecture

```text
Kotlin MainActivity
      │ commands / output callbacks
      ▼
NativeShellSession
      │ JNI
      ▼
PtyBridge.kt ───── libmonnixcode_pty.so
                         │
                         ├─ posix_openpt / grantpt / unlockpt
                         ├─ fork / setsid / TIOCSCTTY
                         ├─ dup2 stdin/stdout/stderr
                         ├─ terminal resize via TIOCSWINSZ
                         └─ exec verified usr/bin/sh -i
```

The shell is started only when all of the following are true:

1. `BootstrapInstaller.status(context) == "verified"`;
2. `files/monnixcode-runtime/usr/bin/sh` exists;
3. the shell file is executable;
4. the native PTY library can be loaded successfully.

If any condition fails, the application displays a warning and does not pretend that a real Linux shell is active.

## Termux-compatible environment

The PTY session exports:

```text
MONNIXCODE_ROOT=<app-private runtime>
PREFIX=<runtime>/usr
HOME=<runtime>/home
TMPDIR=<runtime>/tmp
PATH=<runtime>/usr/bin:/system/bin:/system/xbin
LD_LIBRARY_PATH=<runtime>/usr/lib
TERM=xterm-256color
COLORTERM=truecolor
SHELL=<runtime>/usr/bin/sh
LANG=en_US.UTF-8
```

This allows a correctly built Bootstrap and package set to operate as a self-contained user-space runtime. Package availability depends on the Bootstrap archive and package repositories supplied for the target ABI; the app does not silently fabricate packages.

## Embedded Bootstrap and first launch

The Android build downloads the pinned official `termux-packages` Bootstrap release for four ABIs, verifies each archive by SHA-256, and embeds the archives into the APK. The app then selects the first supported device ABI and installs the matching archive automatically on first launch.

The automatic flow is:

1. Select the device ABI from `Build.SUPPORTED_ABIS`.
2. Open the embedded ABI-specific ZIP archive.
3. Extract its prefix into the app-private `runtime/usr` directory.
4. Restore the archive's `SYMLINKS.txt` entries safely.
5. Validate `usr/bin/sh` and `usr/etc`.
6. Write verified metadata and atomically activate the runtime.

The **Runtime** button reports the current status; there is no manual archive picker in the native app. If the embedded archive is unavailable or fails validation, the app fails closed and shows a warning rather than claiming that a real shell is available.

`BootstrapInstaller` provides both the legacy verified `tar.xz` path for maintainers and the embedded ZIP path used by the app. The embedded path includes SHA-256 pinning, path traversal protection, safe symlink validation, required-file validation, staging, atomic activation, rollback, and a verified status marker.

The embedded archive is a Termux-compatible base user-space, not a complete Termux app. The package name, Android integration, repositories, and available packages may differ. Preserve upstream licenses and notices when redistributing the APK.

For background and troubleshooting, see [`../BOOTSTRAP-GUIDE.md`](../BOOTSTRAP-GUIDE.md).

## Platform and OEM compatibility

Project configuration:

```text
minSdk       35             Android 15
compileSdk   36
 targetSdk   36
JVM target   17
NDK          27.2.12479018
CMake        3.22.1
```

The application uses public Android APIs, app-private storage, and standard NDK PTY primitives. It is designed for AOSP-compatible Android 15+ devices and common OEM distributions. No software can guarantee identical behavior on every vendor because OEMs may change:

- supported CPU ABI and executable formats;
- background execution and battery policies;
- filesystem mount and `noexec` behavior;
- SELinux restrictions;
- document-provider behavior;
- kernel PTY behavior.

For a production device matrix, test at least `arm64-v8a`, the intended Bootstrap archive, screen rotation, background/foreground transitions, low-storage conditions, and the vendor’s battery restrictions.

## Build locally

Use JDK 17 and install Android SDK Platform 36, Build Tools 35.0.0 or compatible, NDK 27.2.12479018, and CMake 3.22.1.

```sh
cd android-app
./gradlew help
./gradlew assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release build:

```sh
./gradlew assembleRelease
```

The default release artifact is unsigned. Configure signing only through a protected release configuration.

## GitHub Actions

Workflow:

```text
../.github/workflows/android-release.yml
```

It installs the Android SDK, NDK, and CMake, then builds debug and release APKs. Tags matching `v*` also receive APK assets in the corresponding GitHub Release.

Current native PTY release:

- planned tag: `v1.2.0`
- embedded Bootstrap release: `bootstrap-2026.11.15-r1+apt.android-7`

## Troubleshooting

### Terminal shows “verified Bootstrap required”

Install a valid ABI-matched archive through the Bootstrap screen. The layout-only first-launch directories are not a Linux runtime and intentionally do not start a shell.

### PTY does not start after installation

Verify that the archive contains an executable `usr/bin/sh`, that the archive ABI matches the device, and that the app has sufficient private storage. Check Android logcat for the `MonnixcodePTY` tag.

### Commands are missing

The shell only contains commands included in the installed Bootstrap and package set. Installing a shell alone does not provide `git`, Python, Node.js, or a package manager.

### App is suspended in the background

Android and OEM battery managers may stop background work. Exclude the app from aggressive battery optimization only when appropriate for the device and user’s policy. The app cannot override vendor power management silently.

## License and notices

The Android application and repository licensing must be reviewed together with the licenses of the Bootstrap, Termux-compatible packages, Apache Commons Compress, Tukaani XZ, and any other included component. Do not redistribute a Bootstrap archive unless its upstream license permits it. See the repository [LICENSE.md](../LICENSE.md) and relevant notices.
