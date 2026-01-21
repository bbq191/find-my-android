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
        // 腾讯地图 SDK Maven 仓库
        maven { url = uri("https://mirrors.tencent.com/repository/maven/tencent_public/") }
        // 阿里云镜像（备用）
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}
rootProject.name = "findmy"
include(":app")
