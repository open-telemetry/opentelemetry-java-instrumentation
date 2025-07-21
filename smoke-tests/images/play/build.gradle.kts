import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  // Don't apply java-conventions since no Java in this project and it interferes with play plugin.
  id("otel.spotless-conventions")

  id("com.google.cloud.tools.jib")
  // Manual configuration - using standard plugins instead of Play plugin
  id("scala")
  id("application")
}

// Modern version management - using latest supported versions
val playVer = "2.8.22"  // Latest Play 2.8.x
val scalaVer = "2.13"   // Upgraded to Scala 2.13 for better performance
val scalaLibVersion = "2.13.10" // Full Scala library version

// Version compatibility check
if (JavaVersion.current() < JavaVersion.VERSION_11) {
  throw GradleException("This project requires Java 11 or higher. Current: ${JavaVersion.current()}")
}

// Application configuration
application {
  mainClass.set("play.core.server.ProdServerStart")
}

// Scala configuration
scala {
  zincVersion.set("1.6.1")
}

dependencies {
  // Scala standard library
  implementation("org.scala-lang:scala-library:$scalaLibVersion")
  
  // Play 2.8.x dependencies with modern Scala 2.13
  implementation("com.typesafe.play:play-server_$scalaVer:$playVer")
  implementation("com.typesafe.play:play-guice_$scalaVer:$playVer")
  implementation("com.typesafe.play:play-logback_$scalaVer:$playVer")
  implementation("com.typesafe.play:filters-helpers_$scalaVer:$playVer")
  
  // Additional Play dependencies for manual setup
  implementation("com.typesafe.play:play_$scalaVer:$playVer")
  implementation("com.typesafe.play:play-akka-http-server_$scalaVer:$playVer")
  
  // Modern Guice versions for better Java compatibility
  implementation("com.google.inject:guice:5.1.0")
  implementation("com.google.inject.extensions:guice-assistedinject:5.1.0")
}

val targetJDK = project.findProperty("targetJDK") ?: "17"  // Updated default to Java 17

val tag = findProperty("tag")
  ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

java {
  // Updated to support modern Java versions while maintaining compatibility
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
  
  // Enable toolchain support for better JDK management
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}

val repo = System.getenv("GITHUB_REPOSITORY") ?: "open-telemetry/opentelemetry-java-instrumentation"

// Custom task to copy Play application resources
val processPlayResources by tasks.registering(Copy::class) {
  from("conf")
  into("${project.layout.buildDirectory.get()}/resources/main")
  include("**/*")
  inputs.dir("conf")
  outputs.dir("${project.layout.buildDirectory.get()}/resources/main")
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Custom task to copy public assets (if they exist)
val processPublicAssets by tasks.registering(Copy::class) {
  from("public") 
  into("${project.layout.buildDirectory.get()}/resources/main/public")
  include("**/*")
  // Configuration cache compatible: use inputs.dir instead of onlyIf
  inputs.dir("public").optional()
  outputs.dir("${project.layout.buildDirectory.get()}/resources/main/public")
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Simple task to handle routes file (for basic smoke test, we'll just copy it)
val processRoutes by tasks.registering(Copy::class) {
  from("conf/routes")
  into("${project.layout.buildDirectory.get()}/resources/main")
  rename("routes", "routes.conf")
  inputs.file("conf/routes")
  outputs.file("${project.layout.buildDirectory.get()}/resources/main/routes.conf")
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Ensure resource tasks run before processResources
tasks.processResources {
  dependsOn(processPlayResources, processPublicAssets, processRoutes)
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

jib {
  from.image = "eclipse-temurin:$targetJDK"
  to.image = "ghcr.io/$repo/smoke-test-play:jdk$targetJDK-$tag"
  container {
    mainClass = "play.core.server.ProdServerStart"
    // Add JVM flags for better performance
    jvmFlags = listOf(
      "-Xms256m",
      "-Xmx512m",
      "-XX:+UseG1GC",
      "-XX:+UseStringDeduplication"
    )
  }
}

// Configure source sets for Play layout
sourceSets {
  main {
    scala {
      srcDirs("app")
    }
    resources {
      // Don't include conf directly since we handle it with custom tasks
      srcDirs("${project.layout.buildDirectory.get()}/resources/main")
    }
  }
}

// Ensure JAR includes compiled classes and resources
tasks.jar {
  dependsOn(tasks.compileScala, tasks.processResources)
  from(sourceSets.main.get().output)
}

// Modern Gradle task configuration - removed Play-specific tasks since we're using manual config
tasks.matching { it.name.startsWith("create") || it.name.startsWith("dist") }.configureEach {
  // Manual configuration doesn't have the same configuration cache issues
  // Improved task inputs for better incremental builds
  inputs.property("playVersion", playVer)
  inputs.property("scalaVersion", scalaVer)
}
