plugins {
    id("com.android.application")
}

android {
    namespace = "com.dagu.hyperstorehelper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dagu.hyperstorehelper"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "1.12.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
}
