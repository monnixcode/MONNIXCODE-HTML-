# MONNIXCODE Workspace Android app

Kotlin Android application for Android 15, 16 and 17+. The app packages the Web Workspace inside a WebView and provides the Android file chooser for the HTML file input.

## Platform configuration

- `minSdk = 35` — Android 15
- `compileSdk = 36` — Android 16 SDK; the app runs on Android 15, 16 and 17+
- `targetSdk = 36` — meets the current Android 16 / Google Play target baseline
- Java/Kotlin JVM target 17
- Kotlin + Android Gradle Plugin via Gradle Kotlin DSL

## Build locally

Install Android Studio with Android SDK Platform 36 and Build Tools 35.0.0, then run from this directory:

```sh
./gradlew assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For a release build:

```sh
./gradlew assembleRelease
```

The default release APK is unsigned. The debug APK is signed with the standard debug key and is suitable for testing. Add a private signing configuration through GitHub Secrets before distributing a production release.

## Bootstrap status

`app/src/main/assets/bootstrap/` contains a verified runtime contract and installer layout. On first launch, `BootstrapInstaller` creates:

```text
files/monnixcode-runtime/
├── home/
├── tmp/
└── usr/
    ├── bin/
    ├── etc/
    └── var/lib/
```

This repository does **not** invent or ship fake shell binaries. A real Termux-compatible Linux bootstrap must be built from a matching `termux-packages` release for each ABI, with checksums, package metadata and licenses. Until those archives are added, the native app is the Web Workspace; full Linux commands run in the Termux source/app separately.

`BootstrapInstaller` now provides the verified installation path for a real archive. After downloading an ABI-matched archive into the app-private storage, call it from a controlled installation flow:

```kotlin
BootstrapInstaller.installArchive(
    context = this,
    archive = downloadedArchive,
    expectedSha256 = manifestSha256,
    requiredFiles = listOf("usr/bin/sh", "usr/etc")
)
```

The installer verifies SHA-256 before extraction, reads `tar.xz` using Apache Commons Compress and Tukaani XZ, rejects unsafe paths and escaping symlinks, validates required files, and switches the staging directory into `files/monnixcode-runtime/` only after the installation is complete. It also records `bootstrap-install.json` with the verified digest. Add the archive and its real checksum to the ABI-specific manifest before enabling this path in production.

## GitHub Actions

Workflow: `.github/workflows/android-release.yml`

- Runs on `v*` tags and manual dispatch.
- Installs Android SDK Platform 36.
- Builds debug and release APKs.
- Uploads both APKs as workflow artifacts.
- On a version tag, attaches APKs to the GitHub Release.

Create a release build:

```sh
git add android-app .github/workflows/android-release.yml
git commit -m "Prepare Android app release"
git push origin main
git tag v1.0.0
git push origin v1.0.0
```

The debug APK is the installable test build. The release APK is unsigned until a signing secret/configuration is added.
