pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GazeBoard"
include(":app")
include(":litert_npu_runtime_libraries:runtime_strings")
include(":litert_npu_runtime_libraries:qualcomm_runtime_v79")
