plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.personal.gpstracker"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.personal.gpstracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
