plugins {
    id("camelcreatives.android.feature")
    id("camelcreatives.android.hilt")
}

android {
    namespace = "com.camelcreatives.rekodi.recorder"
}

dependencies {
    implementation(project(":core:core-data"))
    implementation(libs.lifecycle.service)
    implementation(libs.coroutines.android)
    implementation(libs.media3.exoplayer)
}
