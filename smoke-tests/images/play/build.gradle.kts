import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  // Don't apply java-conventions since no Java in this project and it interferes with play plugin.
  id("otel.spotless-conventions")

  id("com.google.cloud.tools.jib")
  // TODO (trask) this plugin doesn't support Play 2.9+, see https://github.com/gradle/playframework/issues/185
  //  once play 3.1 is released, we can update to https://github.com/orgs/playframework/discussions/12338
  id("org.gradle.playframework") version "0.14"
}

val playVer = "2.8.22"
val scalaVer = "2.12"

play {
  platform {
    playVersion.set(playVer)
    scalaVersion.set(scalaVer)
    javaVersion.set(JavaVersion.VERSION_1_8)
  }
  injectedRoutesGenerator.set(true)
}

dependencies {
  implementation("com.typesafe.play:play-guice_$scalaVer:$playVer")
  // Guice 5.1 is needed for Java 17 support on Play 2.8, see https://github.com/playframework/playframework/releases/tag/2.8.15
  // TODO (trask) remove these version overrides after updating to Play 2.9
  implementation("com.google.inject:guice:5.1.0")
  implementation("com.google.inject.extensions:guice-assistedinject:5.1.0")
  implementation("com.typesafe.play:play-logback_$scalaVer:$playVer")
  implementation("com.typesafe.play:filters-helpers_$scalaVer:$playVer")
}

val targetJDK = project.findProperty("targetJDK") ?: "11"

val tag = findProperty("tag")
  ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

java {
  // this is needed to avoid jib failing with
  // "Your project is using Java 17 but the base image is for Java 8"
  // (it seems the jib plugins does not understand toolchains yet)
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

val repo = System.getenv("GITHUB_REPOSITORY") ?: "open-telemetry/opentelemetry-java-instrumentation"

jib {
  from.image = "eclipse-temurin:$targetJDK"
  to.image = "ghcr.io/$repo/smoke-test-play:jdk$targetJDK-$tag"
  container.mainClass = "play.core.server.ProdServerStart"
}
