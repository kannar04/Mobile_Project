plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.gmail.manhle240406.project"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.gmail.manhle240406.project"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "FIREBASE_EMULATORS", "true")
            buildConfigField("boolean", "COURSE_PROJECT_VERIFIED", "false")
        }
        release {
            buildConfigField("boolean", "FIREBASE_EMULATORS", "false")
            // Change only after operator verification of the course project and rules.
            buildConfigField("boolean", "COURSE_PROJECT_VERIFIED", "false")
            optimization {
                enable = false
            }
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.google.id)
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
