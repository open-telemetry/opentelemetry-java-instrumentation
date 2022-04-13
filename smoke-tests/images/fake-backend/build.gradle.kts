import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  java

  id("com.google.cloud.tools.jib") version "3.2.1"
  id("com.bmuschko.docker-remote-api") version "7.3.0"
  id("com.github.johnrengelman.shadow") version "7.1.2"
  id("de.undercouch.download") version "5.0.4"
  id("com.diffplug.spotless") version "6.4.2"
}

group = "io.opentelemetry"
version = "0.0.1-SNAPSHOT"

repositories {
  mavenCentral()
}

spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("../../../buildscripts/spotless.license.java"), "(package|import|public)")
  }
  kotlinGradle {
    ktlint().userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2", "disabled_rules" to "no-wildcard-imports"))
    target("**/*.gradle.kts")
  }
}

dependencies {
  implementation("com.linecorp.armeria:armeria-grpc:1.15.0")
  implementation("io.opentelemetry.proto:opentelemetry-proto:0.16.0-alpha")
  runtimeOnly("org.slf4j:slf4j-simple:1.7.36")
}

val extraTag = findProperty("extraTag") ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

jib {
  from.image = "gcr.io/distroless/java-debian10:11"
  to.image = "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-fake-backend:$extraTag"
}

// windows containers are built manually since jib does not support windows containers yet
val backendDockerBuildDir = file("$buildDir/docker-backend")

tasks {
  withType<JavaCompile>().configureEach {
    with(options) {
      release.set(11)
    }
  }

  shadowJar {
    manifest {
      attributes(mapOf("Main-Class" to "io.opentelemetry.smoketest.fakebackend.FakeBackendMain"))
    }
  }

  val windowsBackendImagePrepare by registering(Copy::class) {
    dependsOn(shadowJar)
    into(backendDockerBuildDir)
    from("src/docker/backend")
    from(shadowJar.get().outputs) {
      rename { "fake-backend.jar" }
    }
  }

  val windowsBackendImageBuild by registering(DockerBuildImage::class) {
    dependsOn(windowsBackendImagePrepare)
    inputDir.set(backendDockerBuildDir)

    images.add("ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-fake-backend-windows:$extraTag")
    dockerFile.set(File(backendDockerBuildDir, "windows.dockerfile"))
  }

  val dockerPush by registering(DockerPushImage::class) {
    group = "publishing"
    description = "Push all Docker images for the test backend"
    dependsOn(windowsBackendImageBuild)
    images.add("ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-fake-backend-windows:$extraTag")
  }
}
