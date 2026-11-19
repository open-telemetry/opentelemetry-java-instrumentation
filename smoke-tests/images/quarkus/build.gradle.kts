import com.google.cloud.tools.jib.gradle.JibTask
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

buildscript {
  dependencies {
    classpath("com.google.cloud.tools:jib-quarkus-extension-gradle:0.1.2")
  }
}

plugins {
  // otel.java-conventions isn't applied to this module because it adds
  // platform(project(":dependencyManagement")) which conflicts with
  // Quarkus's dependency resolution logic, causing infinite recursion in
  // GradleApplicationModelBuilder.collectDependencies during the configuration phase
  // resulting in StackOverflowError
  id("java")

  id("com.google.cloud.tools.jib")
  id("io.quarkus") version "4.0.0"
}

dependencies {
  implementation(enforcedPlatform("io.quarkus:quarkus-bom:4.0.0"))
  implementation("io.quarkus:quarkus-rest")
}

// Quarkus 4 requires Java 21+
val targetJDK = project.findProperty("targetJDK") ?: "21"

val tag = findProperty("tag")
  ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

val runtimeImages = Properties().apply {
  rootProject.file("smoke-tests/images/java-runtime-images.properties").inputStream().use { load(it) }
}
val latestRuntimeImage = runtimeImages.getProperty("latest")
val fromImage = runtimeImages.getProperty(targetJDK.toString())
  ?: latestRuntimeImage?.takeIf {
    it.substringAfterLast(':').substringBefore('@').takeWhile(Char::isDigit) == targetJDK.toString()
  }
  ?: error("Unsupported targetJDK: $targetJDK")

java {
  // this is needed to avoid jib failing with
  // "Your project is using Java 21 but the base image is for Java 17"
  // (it seems the jib plugins does not understand toolchains yet)
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

val repo = System.getenv("GITHUB_REPOSITORY") ?: "open-telemetry/opentelemetry-java-instrumentation"

jib {
  from.image = fromImage
  to.image = "ghcr.io/$repo/smoke-test-quarkus:jdk$targetJDK-$tag"
  container {
    mainClass = "bogus" // to suppress Jib warning about missing main class
  }
  pluginExtensions {
    pluginExtension {
      implementation = "com.google.cloud.tools.jib.gradle.extension.quarkus.JibQuarkusExtension"
      properties = mapOf("packageType" to "fast-jar")
    }
  }
}

tasks {
  withType<JavaCompile>().configureEach {
    with(options) {
      // Quarkus 4 requires Java 21+
      release.set(21)
    }
  }

  withType<JibTask>().configureEach {
    dependsOn(quarkusBuild)
    // Jib tasks access Task.project at execution time which is not compatible with configuration cache
    notCompatibleWithConfigurationCache("Jib task accesses Task.project at execution time")
  }

  compileJava {
    dependsOn(compileQuarkusGeneratedSourcesJava)
  }
}
