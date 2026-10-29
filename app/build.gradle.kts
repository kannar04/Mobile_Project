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
        val emulatorHost = providers.gradleProperty("firebaseEmulatorHost").orElse("10.0.2.2").get()
        require(emulatorHost.matches(Regex("[A-Za-z0-9.-]+")))
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$emulatorHost\"")
        buildConfigField("int", "FIRESTORE_EMULATOR_PORT", providers.gradleProperty("firebaseFirestoreEmulatorPort").orElse("8180").get())
        buildConfigField("int", "AUTH_EMULATOR_PORT", providers.gradleProperty("firebaseAuthEmulatorPort").orElse("9199").get())
        buildConfigField("int", "FUNCTIONS_EMULATOR_PORT", providers.gradleProperty("firebaseFunctionsEmulatorPort").orElse("5006").get())
    }
    testBuildType = providers.gradleProperty("androidTestBuildType").orElse("debug").get()

    buildTypes {
        debug {
            buildConfigField("boolean", "FIREBASE_EMULATORS", "true")
            buildConfigField("boolean", "COURSE_PROJECT_VERIFIED", "false")
            buildConfigField("String", "EXPECTED_FIREBASE_PROJECT", "\"demo-anilove\"")
            buildConfigField("boolean", "MEDIA_UPLOADS_ENABLED", "false")
            buildConfigField("boolean", "PASSWORD_RESET_CONFIGURATION_VERIFIED", "true")
        }
        create("spark") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            val projectId = providers.gradleProperty("firebaseSparkProject").orElse("aniloveproject").get()
            require(projectId.isEmpty() || projectId.matches(Regex("[a-z][a-z0-9-]{4,28}[a-z0-9]")) && !projectId.startsWith("demo-"))
            buildConfigField("boolean", "FIREBASE_EMULATORS", "false")
            buildConfigField("boolean", "COURSE_PROJECT_VERIFIED", projectId.isNotEmpty().toString())
            buildConfigField("String", "EXPECTED_FIREBASE_PROJECT", "\"$projectId\"")
            buildConfigField("boolean", "MEDIA_UPLOADS_ENABLED", "true")
            // 2026-10-17 read-only project inspection: password provider, enumeration protection,
            // authorized Firebase-hosted domain and default HTTPS action handler verified.
            buildConfigField("boolean", "PASSWORD_RESET_CONFIGURATION_VERIFIED", (projectId == "aniloveproject").toString())
        }
        release {
            buildConfigField("boolean", "FIREBASE_EMULATORS", "false")
            // Change only after operator verification of the course project and rules.
            buildConfigField("boolean", "COURSE_PROJECT_VERIFIED", "false")
            buildConfigField("String", "EXPECTED_FIREBASE_PROJECT", "\"\"")
            buildConfigField("boolean", "MEDIA_UPLOADS_ENABLED", "false")
            buildConfigField("boolean", "PASSWORD_RESET_CONFIGURATION_VERIFIED", "false")
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
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-messaging")
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.google.id)
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
