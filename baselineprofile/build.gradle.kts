plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

kotlin { jvmToolchain(21) }

android {
    namespace = "com.tzii.thysttv.baselineprofile"
    compileSdk = 36
    targetProjectPath = ":app"
    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile { useConnectedDevices = true }

dependencies {
    implementation(libs.benchmark.macro)
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.0")
}
