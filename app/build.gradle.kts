plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cl.rdrp.planilla_shopper"
    compileSdk = 36

    defaultConfig {
        applicationId = "cl.rdrp.planilla_shopper"
        minSdk = 26
        targetSdk = 36
        versionCode = 28
        versionName = "1.0.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }

    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

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
    implementation(libs.gson)
    annotationProcessor(libs.room.compiler)

    // LiveData
    implementation(libs.lifecycle.livedata)

    // Gráficos
    implementation(libs.mpandroidchart)

    // QR Pesable: generar y escanear codigos QR.
    // - zxing-android-embedded incluye Activity de scan + helpers (BarcodeEncoder).
    // - zxing-core es el motor de codificacion/decodificacion (transitivo del
    //   anterior, pero lo declaramos explicito por claridad).
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.2")

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation("androidx.room:room-testing:${libs.versions.room.get()}")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

}

configurations.all {
    resolutionStrategy {
        force("androidx.activity:activity:1.9.2")
    }
}