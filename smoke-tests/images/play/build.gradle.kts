import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  id("org.gradle.playframework") version "0.12"
  id("com.google.cloud.tools.jib") version "3.2.1"
  id("com.diffplug.spotless") version "6.4.2"
}

val playVer = "2.6.20"
val scalaVer = "2.12"

play {
  platform {
    playVersion.set(playVer)
    scalaVersion.set(scalaVer)
    javaVersion.set(JavaVersion.VERSION_1_8)
  }
  injectedRoutesGenerator.set(true)
}

repositories {
  mavenCentral()
  maven {
    setName("lightbend-maven-releases")
    setUrl("https://repo.lightbend.com/lightbend/maven-release")
  }
}

description = "Play Integration Tests."

spotless {
  scala {
    scalafmt()
    licenseHeaderFile(rootProject.file("../../../buildscripts/spotless.license.java"), "(package|import|public)")
    target("src/**/*.scala")
  }
  kotlinGradle {
    ktlint().userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2", "disabled_rules" to "no-wildcard-imports"))
    target("**/*.gradle.kts")
  }
}

dependencies {
  implementation("com.typesafe.play:play-guice_$scalaVer:$playVer")
  implementation("com.typesafe.play:play-logback_$scalaVer:$playVer")
  implementation("com.typesafe.play:filters-helpers_$scalaVer:$playVer")
}

val targetJDK = project.findProperty("targetJDK") ?: "11"

val tag = findProperty("tag") ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

jib {
  from.image = "openjdk:$targetJDK"
  to.image = "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-play:jdk$targetJDK-$tag"
  container.mainClass = "play.core.server.ProdServerStart"
}
