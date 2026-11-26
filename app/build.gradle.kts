plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.hyperdream.module"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "io.github.hyperdream.module"
        minSdk = 31
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0-dev"
    }

    buildTypes {
        release {
            // Keep the entry-point class name intact for assets/xposed_init.
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Supplied by LSPosed at runtime; must never be packaged into the APK.
    compileOnly("de.robv.android.xposed:api:82")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
