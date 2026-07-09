plugins {
    id("camelcreatives.android.feature")
    id("camelcreatives.android.hilt")
}

android {
    namespace = "com.camelcreatives.rekodi.library"
}

dependencies {
    implementation(project(":core:core-data"))
    implementation(libs.coil.compose)
}
