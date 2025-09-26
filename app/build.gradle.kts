plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.indoornavigation"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.indoornavigation"
        minSdk = 24
        targetSdk = 35
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Fix duplicate class error by ensuring ONLY androidx is used
    implementation("androidx.core:core-ktx:1.13.0") // Make sure it's consistent
    implementation("androidx.legacy:legacy-support-v4:1.0.0") // Ensure only androidx version is used

    // Wi-Fi and Network Dependencies
    implementation("androidx.core:core-ktx:1.10.1")


    // Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

