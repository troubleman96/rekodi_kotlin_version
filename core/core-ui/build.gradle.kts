plugins {
    id("camelcreatives.android.library.compose")
}

android {
    namespace = "com.camelcreatives.rekodi.ui"
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(libs.compose.material.icons.extended)
    implementation(libs.coil.compose)
}
