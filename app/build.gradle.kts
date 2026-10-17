plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Supply the same private signing key through GitHub Actions secrets for every update.
val cloudixKeyPath = System.getenv("CLOUDIX_KEYSTORE_PATH")
val cloudixStorePassword = System.getenv("CLOUDIX_STORE_PASSWORD")
val cloudixKeyAlias = System.getenv("CLOUDIX_KEY_ALIAS") ?: "cloudix"
val cloudixKeyPassword = System.getenv("CLOUDIX_KEY_PASSWORD")
val cloudixSigned = !cloudixKeyPath.isNullOrBlank() &&
    !cloudixStorePassword.isNullOrBlank() && !cloudixKeyPassword.isNullOrBlank()
android {
    namespace = "com.cloudix.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.cloudix.app"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 26
        targetSdk = 35
        // Monotonically increasing versionCode prevents update downgrades.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 1000 + it } ?: 2
        versionName = "0.5.0"
    }
    signingConfigs {
        if (cloudixSigned) {
            create("cloudixStable") {
                storeFile = file(cloudixKeyPath!!)
                storePassword = cloudixStorePassword
                keyAlias = cloudixKeyAlias
                keyPassword = cloudixKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (cloudixSigned) signingConfig = signingConfigs.getByName("cloudixStable")
        }
        release {
            isMinifyEnabled = false
            if (cloudixSigned) signingConfig = signingConfigs.getByName("cloudixStable")
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
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/versions/**", "META-INF/OSGI-INF/**")
    }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
