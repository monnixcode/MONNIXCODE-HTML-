# MONNIXCODE Workspace

A native-first Android workspace with a Termux-compatible terminal runtime, mobile file access, verified Bootstrap installation, and a separate browser workspace for public GitHub Pages.

> **Scope:** The Android application provides a real interactive PTY shell inside the app sandbox. It does not obtain Android system root and does not bypass Android security boundaries. No device root is required.

## Product overview

MONNIXCODE Workspace has two complementary targets:

1. **Native Android application** — Kotlin UI, native PTY, app-private Linux runtime, Bootstrap installer, and mobile file picker.
2. **Browser workspace** — standalone HTML workspace for GitHub Pages or a local Termux HTTP server. Browser mode is intentionally limited by browser security and is not a Linux shell.

The Android app is the primary path for Termux-like terminal behavior.

## Android capabilities

- Native Kotlin UI; no WebView is used by `MainActivity`.
- Interactive pseudo-terminal backed by native C++17/JNI code.
- `sh -i` execution with terminal resize, stdin, stdout, and stderr forwarding.
- Termux-compatible runtime variables: `PREFIX`, `HOME`, `TMPDIR`, `PATH`, `LD_LIBRARY_PATH`, `TERM`, and `MONNIXCODE_ROOT`.
- Embedded ABI-specific Termux Bootstrap archives with pinned SHA-256 validation and first-launch auto-install.
- Secure extraction with path traversal and unsafe symlink checks.
- Staging, validation, atomic activation, and rollback.
- Native file selection through Android Storage Access Framework.
- Classic, Cyberpunk, and Matrix themes.
- Android 15+ baseline with standard Android/NDK APIs rather than vendor-specific APIs.

## Security model: “root” without rooting the phone

The app exposes an **app-private runtime root**, for example:

```text
/data/data/com.monnixcode.workspace/files/monnixcode-runtime/
├── home/
├── tmp/
└── usr/
    ├── bin/
    ├── etc/
    ├── lib/
    └── var/
```

Inside this directory the shell can behave like a Termux-style user-space environment. The application owns this directory and can execute the verified ABI-compatible binaries installed there. This is the meaning of “root” in MONNIXCODE documentation.

It is **not** Android system root. The app cannot automatically access `/data` belonging to other apps, alter `/system`, change SELinux policy, mount arbitrary filesystems, or grant itself privileged permissions. Access to shared files is provided through Android’s file picker and only for locations selected by the user.

This design avoids requiring a rooted phone and is the portable option across standard Android devices.

## Embedded Bootstrap installation

The Android build downloads the official `termux-packages` Bootstrap archives for `aarch64`, `arm`, `i686`, and `x86_64` at build time. Each archive is pinned by SHA-256 and embedded into the APK. On first launch, the app selects the device ABI, extracts the matching archive into the app-private `usr` prefix, restores its symlinks, validates `usr/bin/sh`, and activates the runtime atomically.

Users do not need to download or select an archive. The **Runtime** button only reports the embedded runtime status. The build task is intentionally networked and reproducible through the pinned release and checksums; it does not download arbitrary files at runtime.

The embedded Bootstrap supplies a Termux-compatible base user-space. It is not the complete Termux application, and package availability, package-manager behavior, and package paths can differ because this application has a different package name and runtime integration. It also does not provide Android system root.

See [`BOOTSTRAP-GUIDE.md`](BOOTSTRAP-GUIDE.md) for background, ABI terminology, verification, and troubleshooting.

## Android compatibility

The project is configured with:

- `minSdk = 35` — Android 15
- `compileSdk = 36`
- `targetSdk = 36`
- Java/Kotlin target 17
- NDK `27.2.12479018`
- CMake `3.22.1`

The runtime uses public Android APIs, POSIX PTY APIs available through the NDK, and app-private storage. It is designed for AOSP-compatible Android 15 and newer across major OEMs. Actual support can still vary by device ABI, vendor kernel policy, filesystem behavior, battery restrictions, and the Bootstrap archive supplied by the user. “All Android brands” cannot guarantee identical behavior because OEMs control those platform policies.

## Build the Android app

Requirements:

- Android Studio or command-line Android SDK
- Platform 36
- Build Tools 35.0.0 or a compatible installed version
- NDK 27.2.12479018
- CMake 3.22.1
- JDK 17

Build from the repository root:

```sh
cd android-app
./gradlew assembleDebug
```

APK output:

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

For a release build:

```sh
./gradlew assembleRelease
```

The default release APK is unsigned. Configure a private signing key through a secure CI secret before production distribution.

## GitHub Actions and releases

Workflow:

```text
.github/workflows/android-release.yml
```

The workflow:

- runs on `v*` tags or manual dispatch;
- installs Android Platform 36, NDK 27.2.12479018, and CMake 3.22.1;
- builds debug and release APKs;
- uploads APK artifacts;
- attaches APKs to the GitHub Release for the tag.

The embedded Bootstrap release is prepared as `v1.2.0`.

## Browser workspace

Live site:

<https://monnixcode.github.io/MONNIXCODE-HTML-/>

Run locally in Termux:

```sh
pkg update && pkg upgrade -y
pkg install git python -y
git clone https://github.com/monnixcode/MONNIXCODE-HTML-.git
cd MONNIXCODE-HTML-
chmod +x run.sh
./run.sh
```

Open:

```text
http://127.0.0.1:8080
```

Browser mode supports responsive terminal UI, themes, public GitHub file browsing, file selection, folder selection where supported, drag-and-drop, and text preview. It cannot run arbitrary Linux commands, access all phone storage, or provide a real PTY because browser security prevents those operations.

## Repository layout

```text
android-app/                         Native Kotlin Android application
android-app/app/src/main/cpp/        Native PTY implementation and CMake config
android-app/app/src/main/java/       Kotlin activities, installer, and PTY bridge
android-app/app/src/main/assets/     UI assets and Bootstrap contract
index.html                           Browser workspace
assets/                               Browser workspace assets
run.sh                                Local Python HTTP server launcher
TERMUX-GUIDE.md                       Termux installation guide
BOOTSTRAP-GUIDE.md                    Beginner guide for installing a verified Bootstrap archive
termux-source/                        Related Termux source and notices
.github/workflows/                    Pages and Android release workflows
```

## License

The browser edition is licensed under GPLv3; see [LICENSE.md](LICENSE.md). Preserve the licenses and notices of all third-party source and Bootstrap packages, including the material in [`termux-source/`](termux-source/).
