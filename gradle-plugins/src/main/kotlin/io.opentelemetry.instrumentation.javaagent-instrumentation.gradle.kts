plugins {
  id("io.opentelemetry.instrumentation.muzzle-check")
  id("io.opentelemetry.instrumentation.muzzle-generation")
}

// Ensure BOMs apply to muzzle configurations by making them extend from configurations that have BOMs
configurations.configureEach {
  if (name == "muzzleBootstrap" || name == "muzzleTooling" || name == "codegen") {
    // Extend from compileClasspath which should have BOMs if configured
    extendsFrom(configurations.findByName("compileClasspath") ?: configurations.findByName("compileOnly") ?: return@configureEach)
  }
}

dependencies {
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-incubator")
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations-support")
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")

  /*
    Dependencies added to this configuration will be found by the muzzle gradle plugin during code
    generation phase. These classes become part of the code that plugin inspects and traverses during
    references collection phase.
   */
  add("codegen", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")
}
