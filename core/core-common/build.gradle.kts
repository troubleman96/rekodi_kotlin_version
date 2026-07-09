plugins {
    id("camelcreatives.android.library")
}

android {
    namespace = "com.camelcreatives.rekodi.common"
}

dependencies {
    implementation(libs.coroutines.core)
}
