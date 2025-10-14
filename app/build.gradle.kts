plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cl.rdrp.planilla_shopper"
    compileSdk = 34

    defaultConfig {
        applicationId = "cl.rdrp.planilla_shopper"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    buildFeatures {
        viewBinding = true
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

    // UI base (compatibles con compileSdk 34)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.activity)

    // Room (Java)
    implementation(libs.room.runtime)
    implementation(libs.recyclerview)
    implementation(libs.google.material)
    implementation(libs.coordinatorlayout)
    implementation(libs.drawerlayout)
    implementation(libs.androidx.coordinatorlayout)
    annotationProcessor(libs.room.compiler)
    annotationProcessor(libs.room.compiler)

    // LiveData
    implementation(libs.lifecycle.livedata)

    // Gráficos
    implementation(libs.mpandroidchart)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

}

configurations.all {
    resolutionStrategy {
        force("androidx.activity:activity:1.9.2")
    }
}