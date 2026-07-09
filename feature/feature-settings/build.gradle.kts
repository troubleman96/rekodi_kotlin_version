plugins {
    id("camelcreatives.android.feature")
    id("camelcreatives.android.hilt")
}

android {
    namespace = "com.camelcreatives.rekodi.settings"
}

dependencies {
    implementation(project(":core:core-data"))
}
