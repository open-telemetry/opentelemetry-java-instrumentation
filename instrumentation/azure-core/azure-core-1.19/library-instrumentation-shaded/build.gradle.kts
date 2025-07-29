plugins {
  id("com.gradleup.shadow")
  id("otel.java-conventions")
}

group = "io.opentelemetry.javaagent.instrumentation"

dependencies {
  // this is the latest version that works with azure-core 1.19
  // because newer versions use the new fluent ClientLogger.atWarning() from azure-core 1.24
  //
  // note:
  // to look at (potentially incompatible) differences in new versions of the injected artifact, run:
  // git diff azure-core-tracing-opentelemetry_1.0.0-beta.23 azure-core-tracing-opentelemetry_1.0.0-beta.25
  //          -- sdk/core/azure-core-tracing-opentelemetry/src/main
  implementation("com.azure:azure-core-tracing-opentelemetry:1.0.0-beta.23")
}

tasks {
  shadowJar {
    exclude("META-INF/services/*")

    dependencies {
      // including only azure-core-tracing-opentelemetry excludes its transitive dependencies
      include(dependency("com.azure:azure-core-tracing-opentelemetry"))
    }
    relocate("com.azure.core.tracing.opentelemetry", "io.opentelemetry.javaagent.instrumentation.azurecore.v1_19.shaded.com.azure.core.tracing.opentelemetry")
  }

  val extractShadowJar by registering(Copy::class) {
    dependsOn(shadowJar)
    from(zipTree(shadowJar.get().archiveFile))
    into("build/extracted/shadow")
  }
}
