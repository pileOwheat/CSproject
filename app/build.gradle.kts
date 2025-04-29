plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")//google play services
}

android {
    namespace = "com.example.csproject"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.csproject"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

// OkHttp for WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

// Firebase Realtime Database (using BoM for version management)
    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-database")

// AndroidX Fragments
    implementation ("androidx.fragment:fragment:1.8.6")

}
