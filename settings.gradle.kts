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
        // Mapbox Maven 仓库
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            // Mapbox SDK 需要认证，从 gradle.properties 读取 token
            // 在 ~/.gradle/gradle.properties 中添加: MAPBOX_DOWNLOADS_TOKEN=sk.xxx
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                username = "mapbox"
                password = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orNull ?: ""
            }
        }
        // 华为 Maven 仓库
        maven { url = uri("https://developer.huawei.com/repo/") }
        // 个推 Maven 仓库
        maven { url = uri("https://mvn.getui.com/nexus/content/repositories/releases/") }
    }
}

rootProject.name = "findmy"
include(":app")
