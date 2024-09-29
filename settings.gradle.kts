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
        maven { url = uri("https://maven.aliyun.com/repository/public/") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://repo.eclipse.org/content/repositories/paho-releases/") }
        maven { url = uri("https://androidx.dev/snapshots/builds/6787662/artifacts/repository/") }
        maven { url = uri("https://artifact.bytedance.com/repository/Volcengine/") }
        maven { url = uri("https://raw.githubusercontent.com/martinloren/AabResGuard/mvn-repo") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.rongcloud.cn/repository/maven-releases/") }
        maven { url = uri("https://developer.huawei.com/repo/") } // 华为仓库
        maven { url = uri("https://maven.rongcloud.cn/repository/maven-releases/") }
        maven { url = uri("https://androidx.dev/snapshots/builds/6787662/artifacts/repository/") }
        maven { url = uri("https://storage.googleapis.com/r8-releases/raw") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/public/") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://repo.eclipse.org/content/repositories/paho-releases/") }
        maven { url = uri("https://androidx.dev/snapshots/builds/6787662/artifacts/repository/") }
        maven { url = uri("https://artifact.bytedance.com/repository/Volcengine/") }
        maven { url = uri("https://raw.githubusercontent.com/martinloren/AabResGuard/mvn-repo") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.rongcloud.cn/repository/maven-releases/") }
        maven { url = uri("https://developer.huawei.com/repo/") } // 华为仓库
        maven { url = uri("https://maven.rongcloud.cn/repository/maven-releases/") }
        maven { url = uri("https://androidx.dev/snapshots/builds/6787662/artifacts/repository/") }
        maven { url = uri("https://storage.googleapis.com/r8-releases/raw") }
    }
}

rootProject.name = "HomepageV2"
include(":app")
 