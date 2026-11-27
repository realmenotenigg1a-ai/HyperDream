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
        versionCode = 13
        versionName = "1.2-probe"
        val revision = providers.environmentVariable("GITHUB_SHA").map { it.take(8) }.getOrElse("local")
        buildConfigField("String", "BUILD_ID", "\"$revision\"")
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake { arguments += listOf("-DHD_BUILD=${revision}", "-DANDROID_STL=c++_static") }
        }
    }

    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
    }

    buildTypes {
        release {
            // Keep the entry-point class name intact for assets/xposed_init.
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
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
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Supplied by LSPosed at runtime; must never be packaged into the APK.
    compileOnly("de.robv.android.xposed:api:82")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
