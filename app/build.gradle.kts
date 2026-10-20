plugins { id("com.android.application") }
android {
    namespace = "fr.centauruslab.test"
    compileSdk = 35
    defaultConfig {
        applicationId = "fr.centauruslab.test"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
