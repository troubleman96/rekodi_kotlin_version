plugins {
    `kotlin-dsl`
}

group = "com.camelcreatives.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplicationCompose") {
            id = "camelcreatives.android.application.compose"
            implementationClass = "com.camelcreatives.buildlogic.AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "camelcreatives.android.library.compose"
            implementationClass = "com.camelcreatives.buildlogic.AndroidLibraryComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "camelcreatives.android.library"
            implementationClass = "com.camelcreatives.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "camelcreatives.android.feature"
            implementationClass = "com.camelcreatives.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "camelcreatives.android.hilt"
            implementationClass = "com.camelcreatives.buildlogic.AndroidHiltConventionPlugin"
        }
    }
}
