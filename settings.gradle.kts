pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/k2-fsa/sherpa-ncnn")
            credentials {
                username = "token"
                password = System.getenv("GITHUB_TOKEN") ?: System.getenv("GPR_API_KEY_READ")
            }
        }
    }
}

rootProject.name = "LiveGG1"
include(":app")
 