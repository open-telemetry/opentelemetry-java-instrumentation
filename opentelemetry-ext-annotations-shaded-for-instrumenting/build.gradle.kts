plugins {
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "opentelemetry-extension-annotations shaded for internal javaagent usage"
group = "io.opentelemetry.javaagent"

val shadowInclude by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

dependencies {
  shadowInclude("io.opentelemetry:opentelemetry-extension-annotations")
  shadowInclude("io.opentelemetry:opentelemetry-context")
}

// OpenTelemetry API shaded so that it can be used in instrumentation of OpenTelemetry API itself,
// and then its usage can be unshaded after OpenTelemetry API is shaded
// (see more explanation in opentelemetry-api-1.0.gradle)
tasks {
  jar {
    enabled = false
  }

  shadowJar {
    configurations = listOf(shadowInclude)
    archiveClassifier.set("")
    relocate("io.opentelemetry", "application.io.opentelemetry")
  }

  publishing {
    publications {
      named<MavenPublication>("maven") {
        // shadow does not use default configurations
        project.shadow.component(this)
        // force jar packaging
        pom {
          packaging = "jar"
        }
      }
    }
  }
}
