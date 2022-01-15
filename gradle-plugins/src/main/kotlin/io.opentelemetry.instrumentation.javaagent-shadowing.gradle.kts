import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("com.github.johnrengelman.shadow")
}

tasks.withType<ShadowJar>().configureEach {
  mergeServiceFiles()
  // Merge any AWS SDK service files that may be present (too bad they didn't just use normal
  // service loader...)
  mergeServiceFiles("software/amazon/awssdk/global/handlers")

  exclude("**/module-info.class")

  // Prevents conflict with other SLF4J instances. Important for premain.
  relocate("org.slf4j", "io.opentelemetry.javaagent.slf4j")
  // rewrite dependencies calling Logger.getLogger
  relocate("java.util.logging.Logger", "io.opentelemetry.javaagent.bootstrap.PatchLogger")

  // prevents conflict with library instrumentation
  relocate("io.opentelemetry.instrumentation", "io.opentelemetry.javaagent.shaded.instrumentation")

  // relocate(OpenTelemetry API)
  relocate("io.opentelemetry.api", "io.opentelemetry.javaagent.shaded.io.opentelemetry.api")
  relocate("io.opentelemetry.semconv", "io.opentelemetry.javaagent.shaded.io.opentelemetry.semconv")
  relocate("io.opentelemetry.context", "io.opentelemetry.javaagent.shaded.io.opentelemetry.context")

  // relocate(the OpenTelemetry extensions that are used by instrumentation modules)
  // these extensions live in the AgentClassLoader, and are injected into the user's class loader
  // by the instrumentation modules that use them
  relocate("io.opentelemetry.extension.aws", "io.opentelemetry.javaagent.shaded.io.opentelemetry.extension.aws")
  relocate("io.opentelemetry.extension.kotlin", "io.opentelemetry.javaagent.shaded.io.opentelemetry.extension.kotlin")

  // this is for instrumentation on opentelemetry-api itself
  relocate("application.io.opentelemetry", "io.opentelemetry")

  // this is for instrumentation on java.util.logging (since java.util.logging itself is shaded above)
  relocate("application.java.util.logging", "java.util.logging")
}
