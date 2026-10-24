import org.springframework.boot.loader.tools.Libraries
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

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.jar {
  doLast {
    // Use the legacy packager, not the modern Boot plugin's launcher.
    Repackager(archiveFile.get().asFile).apply {
      setMainClass("io.opentelemetry.smoketest.springboot13.ResourceApplication")
      setBackupSource(false)
      repackage(Libraries.NONE)
    }
  }
}
