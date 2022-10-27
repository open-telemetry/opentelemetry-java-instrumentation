pluginManagement {
  plugins {
    id("com.bmuschko.docker-remote-api") version "7.3.0"
    id("com.github.ben-manes.versions") version "0.42.0"
    id("com.github.jk1.dependency-license-report") version "2.1"
    id("com.google.cloud.tools.jib") version "3.2.1"
    id("com.gradle.plugin-publish") version "1.0.0"
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("org.jetbrains.kotlin.jvm") version "1.6.20"
    id("org.unbroken-dome.test-sets") version "4.0.0"
    id("org.xbib.gradle.plugin.jflex") version "1.6.0"
    id("org.unbroken-dome.xjc") version "2.0.0"
  }
}

plugins {
  id("com.gradle.enterprise") version "3.11.2"
  id("com.github.burrunan.s3-build-cache") version "1.3"
  id("com.gradle.common-custom-user-data-gradle-plugin") version "1.8.2"
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    mavenLocal()
  }
}

val gradleEnterpriseServer = "https://ge.opentelemetry.io"
val isCI = System.getenv("CI") != null
val geAccessKey = System.getenv("GRADLE_ENTERPRISE_ACCESS_KEY") ?: ""

// if GE access key is not given and we are in CI, then we publish to scans.gradle.com
val useScansGradleCom = isCI && geAccessKey.isEmpty()

if (useScansGradleCom) {
  gradleEnterprise {
    buildScan {
      termsOfServiceUrl = "https://gradle.com/terms-of-service"
      termsOfServiceAgree = "yes"
      isUploadInBackground = !isCI
      publishAlways()

      capture {
        isTaskInputFiles = true
      }
    }
  }
} else {
  gradleEnterprise {
    server = gradleEnterpriseServer
    buildScan {
      isUploadInBackground = !isCI

      this as com.gradle.enterprise.gradleplugin.internal.extension.BuildScanExtensionWithHiddenFeatures
      publishIfAuthenticated()
      publishAlways()

      capture {
        isTaskInputFiles = true
      }

      gradle.startParameter.projectProperties["testJavaVersion"]?.let { tag(it) }
      gradle.startParameter.projectProperties["testJavaVM"]?.let { tag(it) }
      gradle.startParameter.projectProperties["smokeTestSuite"]?.let {
        value("Smoke test suite", it)
      }
    }
  }
}

val geCacheUsername = System.getenv("GE_CACHE_USERNAME") ?: ""
val geCachePassword = System.getenv("GE_CACHE_PASSWORD") ?: ""
buildCache {
  remote<HttpBuildCache> {
    url = uri("$gradleEnterpriseServer/cache/")
    isPush = isCI && geCacheUsername.isNotEmpty()
    credentials {
      username = geCacheUsername
      password = geCachePassword
    }
  }
}

rootProject.name = "opentelemetry-java-instrumentation"

// this is only split out due to a dependabot limitation
// for details see .github/project-root-duplicates/README.md
apply(from = "includes.gradle.kts")
