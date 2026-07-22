rootProject.name = "Agora"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()

        // Kotlin/Wasm: descarga del toolchain Node.js + Yarn que webpack necesita
        // para wasmJsBrowserDistribution. Con repositoriesMode = PREFER_SETTINGS,
        // Gradle ignora los repos que registran los plugins de Kotlin, así que hay
        // que declararlos aquí. Van scopeados por contenido (solo el binario de
        // Node y el de Yarn) para no tocar la resolución del resto — Android intacto.
        ivy("https://nodejs.org/dist/") {
            name = "Node.js Distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn Distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen Distributions"
            patternLayout { artifact("version_[revision]/[artifact]-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

include(":composeApp")
include(":core:model")
include(":core:data")
include(":core:domain")
include(":core:ui")
include(":core:common")
include(":feature:auth")
include(":feature:community")
include(":feature:activity")
include(":feature:reservation")
include(":feature:notification")
