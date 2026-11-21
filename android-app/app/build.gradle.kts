import org.gradle.api.tasks.Copy

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.monnixcode.workspace"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.monnixcode.workspace"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

val syncWebAssets = tasks.register<Copy>("syncWebAssets") {
    from(rootProject.file("../index.html"))
    into(layout.projectDirectory.dir("src/main/assets/web"))
    from(rootProject.file("../assets")) { into("assets") }
}

tasks.named("preBuild") { dependsOn(syncWebAssets) }
