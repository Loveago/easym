plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.easymomo.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.easy.momo"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Backend API base URL (overridable via -PAPI_BASE_URL or gradle.properties)
        val configuredApiBaseUrl = (project.findProperty("API_BASE_URL") as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "https://backend-omega-blue-30.vercel.app/api/"
        val normalizedApiBaseUrl = if (configuredApiBaseUrl.endsWith("/")) {
            configuredApiBaseUrl
        } else {
            "$configuredApiBaseUrl/"
        }
        buildConfigField("String", "API_BASE_URL", "\"$normalizedApiBaseUrl\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file(
                (project.findProperty("RELEASE_STORE_FILE") as? String)
                    ?: "easymomo-release-key.jks"
            )
            storePassword = (project.findProperty("RELEASE_STORE_PASSWORD") as? String)
                ?: "EasyMoMo@2026!"
            keyAlias = (project.findProperty("RELEASE_KEY_ALIAS") as? String)
                ?: "easymomo"
            keyPassword = (project.findProperty("RELEASE_KEY_PASSWORD") as? String)
                ?: "EasyMoMo@2026!"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "DebugProbesKt.bin"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.security.crypto)
    implementation(libs.browser)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.arch.core.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
