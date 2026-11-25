plugins { id("com.android.application") }
android {
    namespace = "kr.hwpx.driveviewer"
    compileSdk = 35
    defaultConfig {
        applicationId = "kr.hwpx.driveviewer"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "2.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}