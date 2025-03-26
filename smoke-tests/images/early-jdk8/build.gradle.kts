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
  val dockerWorkingDir = layout.buildDirectory.dir("docker")

  val imagePrepare by registering(Copy::class) {
    into(dockerWorkingDir)
    from("Dockerfile")
  }

  val repo = System.getenv("GITHUB_REPOSITORY") ?: "open-telemetry/opentelemetry-java-instrumentation"

  val imageBuild by registering(DockerBuildImage::class) {
    dependsOn(imagePrepare)
    inputDir.set(dockerWorkingDir)

    images.add("ghcr.io/$repo/smoke-test-zulu-openjdk-8u31:$extraTag")
    dockerFile.set(dockerWorkingDir.get().file("Dockerfile"))
  }

  val dockerPush by registering(DockerPushImage::class) {
    group = "publishing"
    description = "Push all Docker images"
    dependsOn(imageBuild)
    images.add("ghcr.io/$repo/smoke-test-zulu-openjdk-8u31:$extraTag")
  }
}
