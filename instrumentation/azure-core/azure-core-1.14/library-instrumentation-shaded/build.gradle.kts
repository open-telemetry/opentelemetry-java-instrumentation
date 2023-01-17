plugins {
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
}

group = "io.opentelemetry.javaagent.instrumentation"

dependencies {
  // this is the latest version that works with azure-core 1.14
  implementation("com.azure:azure-core-tracing-opentelemetry:1.0.0-beta.12")
}

tasks {
  shadowJar {
    exclude("META-INF/services/*")

    dependencies {
      // including only azure-core-tracing-opentelemetry excludes its transitive dependencies
      include(dependency("com.azure:azure-core-tracing-opentelemetry"))
    }
    relocate("com.azure.core.tracing.opentelemetry", "io.opentelemetry.javaagent.instrumentation.azurecore.v1_14.shaded.com.azure.core.tracing.opentelemetry")
  }

  val extractShadowJar by registering(Copy::class) {
    dependsOn(shadowJar)
    from(zipTree(shadowJar.get().archiveFile))
    into("build/extracted/shadow")
  }
}
