import java.net.URL
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val bootstrapRelease = "bootstrap-2026.11.15-r1+apt.android-7"
val bootstrapArchives = mapOf(
    "aarch64" to Pair("388acf91cc247e7062d80cd8aa791fe72f360515fb78dc54ff45742120facae7", "bootstrap-aarch64.zip"),
    "arm" to Pair("ff77211b7e71366b494d6b7d04211081b94de4a291ec5f783bc4ce77333a0c92", "bootstrap-arm.zip"),
    "i686" to Pair("bb397db1a71c65ce8a0928b0c3015c0926f523726214a182e098570d15789afe", "bootstrap-i686.zip"),
    "x86_64" to Pair("fb95765f2ffb4cf5648891199dedb820ed19a63334352fb0bc69b7b7c917e002", "bootstrap-x86_64.zip")
)
val generatedBootstrapAssets = layout.buildDirectory.dir("generated/bootstrap-assets")

fun sha256(file: java.io.File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val downloadBootstrapArchives = tasks.register("downloadBootstrapArchives") {
    outputs.dir(generatedBootstrapAssets)
    doLast {
        val outputDir = generatedBootstrapAssets.get().asFile.resolve("bootstrap/archives")
        outputDir.mkdirs()
        bootstrapArchives.forEach { (_, details) ->
            val (expectedSha256, fileName) = details
            val destination = outputDir.resolve(fileName)
            if (!destination.isFile || sha256(destination) != expectedSha256) {
                val url = "https://github.com/termux/termux-packages/releases/download/" +
                    "${bootstrapRelease.replace("+", "%2B")}/$fileName"
                URL(url).openStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                check(sha256(destination) == expectedSha256) {
                    "Bootstrap SHA-256 mismatch: $fileName"
                }
            }
        }
    }
}

android {
    namespace = "com.monnixcode.workspace"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.monnixcode.workspace"
        minSdk = 35
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    sourceSets["main"].assets.srcDir(generatedBootstrapAssets)
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

tasks.named("preBuild").configure { dependsOn(downloadBootstrapArchives) }

dependencies {
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
}
