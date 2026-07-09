plugins {
    id("camelcreatives.android.library")
    id("camelcreatives.android.hilt")
}

android {
    namespace = "com.camelcreatives.rekodi.data"
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
}
