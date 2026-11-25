plugins {
    id("com.android.application")
}

android {
    namespace = "kr.hwpx.driveviewer"
    compileSdk = 34

    defaultConfig {
        applicationId = "kr.hwpx.driveviewer"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
}
