import java.time.Duration

plugins {
  id("idea")

  id("com.github.ben-manes.versions")
  id("io.github.gradle-nexus.publish-plugin")
  id("otel.spotless-conventions")
}

apply(from = "version.gradle.kts")

nexusPublishing {
  packageGroup.set("io.opentelemetry")

  repositories {
    sonatype {
      username.set(System.getenv("SONATYPE_USER"))
      password.set(System.getenv("SONATYPE_KEY"))
    }
  }

  connectTimeout.set(Duration.ofMinutes(5))
  clientTimeout.set(Duration.ofMinutes(5))

  transitionCheckOptions {
    // We have many artifacts so Maven Central takes a long time on its compliance checks. This sets
    // the timeout for waiting for the repository to close to a comfortable 50 minutes.
    maxRetries.set(300)
    delayBetween.set(Duration.ofSeconds(10))
  }
}

//仓库配置
repositories {
    //mavenLocal { setUrl("file://${project.rootDir}/lib") }
    //首先去本地仓库找
    mavenLocal()
    //然后去阿里仓库找
    // build.gradle:
    // maven { url "https://maven.aliyun.com/nexus/content/groups/public/" }

    // build.gradle.kts:
    maven { url = uri("https://repo.spring.io/release") }
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://plugins.gradle.org/m2/") }
    maven {
        isAllowInsecureProtocol = true
        setUrl("https://maven.aliyun.com/nexus/content/groups/public/")
    }
    maven {
        isAllowInsecureProtocol = true
        url = uri("https://maven.aliyun.com/repository/public") }
    maven {
        isAllowInsecureProtocol = true
        url = uri("https://maven.aliyun.com/repository/google") }
    maven {
        isAllowInsecureProtocol = true
        url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    maven {
        isAllowInsecureProtocol = true
        url = uri("https://maven.aliyun.com/repository/spring-plugin") }
    maven {
        isAllowInsecureProtocol = true
        url = uri("https://maven.aliyun.com/repository/apache-snapshots") }
    maven {
        isAllowInsecureProtocol = true
        url = uri("https://oss.jfrog.org/artifactory/oss-snapshot-local/") }
    google()
    //最后从maven中央仓库找
    mavenCentral()
}

description = "OpenTelemetry instrumentations for Java"
