import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  id("otel.java-conventions")

  id("com.bmuschko.docker-remote-api")
  id("com.github.johnrengelman.shadow")
  id("com.google.cloud.tools.jib")
}

dependencies {
  implementation("com.linecorp.armeria:armeria-grpc:1.15.0")
  implementation("io.opentelemetry.proto:opentelemetry-proto")
  runtimeOnly("org.slf4j:slf4j-simple")
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

  javadoc {
    isEnabled = false
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
