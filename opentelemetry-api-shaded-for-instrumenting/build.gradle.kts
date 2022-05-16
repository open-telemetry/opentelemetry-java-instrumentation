import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
}

description = "opentelemetry-api shaded for internal javaagent usage"
group = "io.opentelemetry.javaagent"

val latestDeps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}
val v1_10Deps by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
}

// configuration for publishing the shadowed artifact
val v1_10 by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}

dependencies {
  latestDeps("io.opentelemetry:opentelemetry-api")

  listOf("opentelemetry-api", "opentelemetry-context").forEach {
    v1_10Deps("io.opentelemetry:$it") {
      version {
        strictly("1.10.0")
      }
    }
  }
}

// OpenTelemetry API shaded so that it can be used in instrumentation of OpenTelemetry API itself,
// and then its usage can be unshaded after OpenTelemetry API is shaded
// (see more explanation in opentelemetry-api-1.0.gradle)
tasks {
  withType<ShadowJar>().configureEach {
    relocate("io.opentelemetry", "application.io.opentelemetry")
  }

  shadowJar {
    configurations = listOf(latestDeps)
  }

  val v1_10Shadow by registering(ShadowJar::class) {
    configurations = listOf(v1_10Deps)
    archiveClassifier.set("v1_10")
  }

  artifacts {
    add(v1_10.name, v1_10Shadow)
  }
}
