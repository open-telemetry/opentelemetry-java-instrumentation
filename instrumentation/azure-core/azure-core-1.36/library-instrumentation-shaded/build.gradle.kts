plugins {
  id("com.gradleup.shadow")
  id("otel.java-conventions")
}

group = "io.opentelemetry.javaagent.instrumentation"

dependencies {
  // the latest version that works with azure-core 1.36.0 is 1.0.0-beta.49
  // but beta.49's reflective AgentContextStorage lookup can throw LinkageError across the
  // application/agent classloader boundary; beta.51 handles this
  // (https://github.com/Azure/azure-sdk-for-java/pull/42586)
  implementation("com.azure:azure-core-tracing-opentelemetry:1.0.0-beta.45")
}

tasks {
  shadowJar {
    exclude("META-INF/services/*")

    dependencies {
      // including only azure-core-tracing-opentelemetry excludes its transitive dependencies
      include(dependency("com.azure:azure-core-tracing-opentelemetry"))
    }
    relocate("com.azure.core.tracing.opentelemetry", "io.opentelemetry.javaagent.instrumentation.azurecore.v1_36.shaded.com.azure.core.tracing.opentelemetry")
  }

  register<Copy>("extractShadowJar") {
    dependsOn(shadowJar)
    from(zipTree(shadowJar.get().archiveFile))
    into("build/extracted/shadow")
  }
}
