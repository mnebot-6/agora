import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "coreData"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(projects.core.common)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.storage)

            implementation(libs.ktor.client.core)
            implementation(libs.koin.core)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

// Generate SupabaseConfig.kt from local.properties
val generateSupabaseConfig by tasks.registering {
    val localPropsFile = rootProject.file("local.properties")
    val outputDir = layout.buildDirectory.dir("generated/supabaseConfig")

    inputs.file(localPropsFile).optional()
    outputs.dir(outputDir)

    doLast {
        val props = Properties()
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { props.load(it) }
        }
        val url = props.getProperty("SUPABASE_URL", "")
        val key = props.getProperty("SUPABASE_ANON_KEY", "")

        val dir = outputDir.get().asFile.resolve("com/app/community/core/data")
        dir.mkdirs()
        dir.resolve("SupabaseConfig.kt").writeText(
            buildString {
                appendLine("package com.app.community.core.data")
                appendLine()
                appendLine("object SupabaseConfig {")
                appendLine("    const val URL = \"${url}\"")
                appendLine("    const val ANON_KEY = \"${key}\"")
                appendLine("}")
            }
        )
    }
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir(generateSupabaseConfig.map { it.outputs.files.singleFile })
}

android {
    namespace = "com.app.community.core.data"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
