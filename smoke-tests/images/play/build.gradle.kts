import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  // Don't apply java-conventions since no Java in this project and it interferes with play plugin.
  id("otel.spotless-conventions")

  id("com.google.cloud.tools.jib")
  id("org.gradle.playframework") version "0.14"
}

val playVer = "2.8.20"
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

jib {
  from.image = "eclipse-temurin:$targetJDK"
  to.image = "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-play:jdk$targetJDK-$tag"
  container.mainClass = "play.core.server.ProdServerStart"
}
