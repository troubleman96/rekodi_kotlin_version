plugins {
    id("camelcreatives.android.feature")
    id("camelcreatives.android.hilt")
}

android {
    namespace = "com.camelcreatives.rekodi.editor"
}

dependencies {
    implementation(project(":core:core-data"))
    implementation(libs.coroutines.android)
    implementation(libs.media3.exoplayer)
}
