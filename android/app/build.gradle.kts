plugins {
    id("com.android.application")
}

android {
    namespace = "com.academia.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.academia.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Assina o APK de release com a chave de debug para que possa ser instalado direto no celular
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
