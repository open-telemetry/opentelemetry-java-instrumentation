import org.gradle.jvm.toolchain.JavaLanguageVersion
import play.gradle.Language
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  // Don't apply java-conventions since no Java in this project and it interferes with play plugin.
  id("otel.spotless-conventions")

  id("com.google.cloud.tools.jib")
  id("org.playframework.play") version "3.1.0-M4"
}

play {
  lang.set(Language.SCALA)
}

dependencies {
  val playVersion = "3.1.0-M4"
  val scalaVersion = "2.13"

  implementation("org.playframework:play-guice_$scalaVersion:$playVersion")
  implementation("org.playframework:play-logback_$scalaVersion:$playVersion")
  implementation("org.playframework:play-filters-helpers_$scalaVersion:$playVersion")
  runtimeOnly("org.playframework:play-server_$scalaVersion:$playVersion")
  runtimeOnly("org.playframework:play-pekko-http-server_$scalaVersion:$playVersion")
  runtimeOnly("org.apache.pekko:pekko-http_$scalaVersion:1.3.0")
}

val targetJDK = (project.findProperty("targetJDK") as String?) ?: "17"
val javaLanguageVersion = targetJDK.toIntOrNull() ?: 17

val tag = findProperty("tag")
  ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(javaLanguageVersion))
}

val repo = System.getenv("GITHUB_REPOSITORY") ?: "open-telemetry/opentelemetry-java-instrumentation"

jib {
  from.image = "eclipse-temurin:$targetJDK"
  to.image = "ghcr.io/$repo/smoke-test-play:jdk$targetJDK-$tag"
  container.mainClass = "play.core.server.ProdServerStart"
}
