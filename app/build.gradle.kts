plugins {
    id("camelcreatives.android.application.compose")
    id("camelcreatives.android.hilt")
}

android {
    namespace = "com.camelcreatives.rekodi"

    defaultConfig {
        applicationId = "com.camelcreatives.rekodi"
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-common"))
    implementation(project(":feature:feature-recorder"))
    implementation(project(":feature:feature-editor"))
    implementation(project(":feature:feature-library"))
    implementation(project(":feature:feature-settings"))
    implementation(project(":feature:feature-onboarding"))

    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.lifecycle.service)

    testImplementation(libs.junit)
}
