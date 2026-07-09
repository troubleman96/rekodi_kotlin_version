package com.camelcreatives.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            extensions.configure<LibraryExtension> {
                compileSdk = 35
                defaultConfig { minSdk = 26 }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                buildFeatures { compose = true }
            }

            tasks.withType<KotlinCompile>().configureEach {
                kotlinOptions { jvmTarget = "17" }
            }

            dependencies {
                add("implementation", project(":core:core-ui"))
                add("implementation", project(":core:core-common"))
                add("implementation", platform(libs.findLibrary("compose-bom").get()))
                add("implementation", libs.findLibrary("compose-ui").get())
                add("implementation", libs.findLibrary("compose-material3").get())
                add("implementation", libs.findLibrary("compose-foundation").get())
                add("implementation", libs.findLibrary("compose-animation").get())
                add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
                add("implementation", libs.findLibrary("activity-compose").get())
                add("implementation", libs.findLibrary("lifecycle-runtime-compose").get())
                add("implementation", libs.findLibrary("lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("navigation-compose").get())
                add("implementation", libs.findLibrary("hilt-navigation-compose").get())
                add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
            }
        }
    }
}
