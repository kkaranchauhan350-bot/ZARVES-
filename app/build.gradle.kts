plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
dependencies { implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.5") }
android {
    namespace = "com.zarves.ai"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.zarves.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
