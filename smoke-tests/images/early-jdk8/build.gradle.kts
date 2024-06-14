import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  id("com.bmuschko.docker-remote-api")
}

val extraTag = findProperty("extraTag")
  ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

tasks {
  val imageBuild by registering(DockerBuildImage::class) {
    images.add("ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-zulu-openjdk-8u31:$extraTag")
    dockerFile.set(File("Dockerfile"))
  }

  val dockerPush by registering(DockerPushImage::class) {
    group = "publishing"
    description = "Push all Docker images"
    dependsOn(imageBuild)
    images.add("ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-zulu-openjdk-8u31:$extraTag")
  }
}
