import org.springframework.boot.loader.tools.Library
import org.springframework.boot.loader.tools.LibraryScope
import org.springframework.boot.loader.tools.Repackager

buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath("org.springframework.boot:spring-boot-loader-tools:1.3.8.RELEASE")
  }
}

plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.apache.logging.log4j:log4j-api:2.26.1")
  implementation("org.apache.logging.log4j:log4j-core:2.26.1")
}

tasks.jar {
  val runtimeClasspath = configurations.named("runtimeClasspath").map { it.files }
  inputs.files(runtimeClasspath)

  doLast {
    // Use the legacy packager, not the modern Boot plugin's launcher.
    Repackager(archiveFile.get().asFile).apply {
      setMainClass("io.opentelemetry.smoketest.springboot13.LogCorrelationApplication")
      setBackupSource(false)
      repackage { callback ->
        runtimeClasspath.get()
          .filter { it.isFile && it.extension == "jar" }
          .forEach { callback.library(Library(it, LibraryScope.COMPILE)) }
      }
    }
  }
}
