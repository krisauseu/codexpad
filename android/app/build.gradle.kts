plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val serverUrl = providers.gradleProperty("codexpad.serverUrl").orElse("https://pad.feichti.dev").get()
require(serverUrl.matches(Regex("https?://[^\\s\"\\\\]+"))) { "Invalid codexpad.serverUrl" }

android {
    namespace = "dev.codexpad"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.codexpad"
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "dev.codexpad.KeystoreTestRunner"
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.withType<Test>().configureEach {
    val contractUrl = providers.environmentVariable("CODEXPAD_CONTRACT_URL")
    inputs.property("contractServer", contractUrl.orElse(""))
    // A running fixture is external state; an opt-in integration run must execute afresh.
    if (contractUrl.isPresent) outputs.upToDateWhen { false }
}
