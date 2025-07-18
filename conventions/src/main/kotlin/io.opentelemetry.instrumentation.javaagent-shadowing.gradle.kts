import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("com.gradleup.shadow")
}

// NOTE: any modifications below should also be made in
//       io.opentelemetry.instrumentation.muzzle-check.gradle.kts
tasks.withType<ShadowJar>().configureEach {
  mergeServiceFiles()
  // Merge any AWS SDK service files that may be present (too bad they didn't just use normal
  // service loader...)
  mergeServiceFiles("software/amazon/awssdk/global/handlers")

  exclude("**/module-info.class")

  // rewrite dependencies calling Logger.getLogger
  relocate("java.util.logging.Logger", "io.opentelemetry.javaagent.bootstrap.PatchLogger")

  if (project.findProperty("disableShadowRelocate") != "true") {
    // prevents conflict with library instrumentation, since these classes live in the bootstrap class loader
    relocate("io.opentelemetry.instrumentation", "io.opentelemetry.javaagent.shaded.instrumentation") {
      // Exclude resource providers since they live in the agent class loader
      exclude("io.opentelemetry.instrumentation.resources.*")
      exclude("io.opentelemetry.instrumentation.spring.resources.*")
    }

    // relocate(OpenTelemetry API) since these classes live in the bootstrap class loader
    relocate("io.opentelemetry.api", "io.opentelemetry.javaagent.shaded.io.opentelemetry.api")
    relocate("io.opentelemetry.semconv", "io.opentelemetry.javaagent.shaded.io.opentelemetry.semconv")
    relocate("io.opentelemetry.context", "io.opentelemetry.javaagent.shaded.io.opentelemetry.context")
    relocate("io.opentelemetry.common", "io.opentelemetry.javaagent.shaded.io.opentelemetry.common")
  }

  // relocate(the OpenTelemetry extensions that are used by instrumentation modules)
  // these extensions live in the AgentClassLoader, and are injected into the user's class loader
  // by the instrumentation modules that use them
  relocate("io.opentelemetry.contrib.awsxray", "io.opentelemetry.javaagent.shaded.io.opentelemetry.contrib.awsxray")
  relocate("io.opentelemetry.extension.kotlin", "io.opentelemetry.javaagent.shaded.io.opentelemetry.extension.kotlin")

  // this is for instrumentation of opentelemetry-api and opentelemetry-instrumentation-api
  relocate("application.io.opentelemetry", "io.opentelemetry")
  relocate("application.io.opentelemetry.instrumentation.api", "io.opentelemetry.instrumentation.api")

  // this is for instrumentation on java.util.logging (since java.util.logging itself is shaded above)
  relocate("application.java.util.logging", "java.util.logging")
}
