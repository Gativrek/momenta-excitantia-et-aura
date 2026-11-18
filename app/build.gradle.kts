import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "io.github.gativrek.momenta"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.gativrek.momenta"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // JVM unit tests run against android.jar stubs. Return defaults instead of throwing so
        // pure mapping code that touches Media3/Bundle classes can still be tested without Robolectric.
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
    }

    lint {
        // "No new warnings" (KICKOFF.md): any lint warning fails the build.
        warningsAsErrors = true
        abortOnError = true
        // Version-bump nags depend on what the machine's SDK knows about (CI's is newer than
        // a dev box's), so they'd make the build flaky. Upgrade deliberately instead.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Make the documented check command (`./gradlew lint test assembleDebug`) include ktlint.
tasks.named("lint") { dependsOn("ktlintCheck") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    // Coil core only: deliberately no coil-network-* module, the app never fetches over the network.
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
