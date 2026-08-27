plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.dshdroid.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.dshdroid.app"
        minSdk = 26
        // targetSdk 28: 允许执行 app 数据目录内的二进制（proot 需要，参考 Termux）
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "META-INF/*" }
}
dependencies { implementation("org.apache.commons:commons-compress:1.26.2") }
