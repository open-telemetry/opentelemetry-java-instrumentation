plugins {
  id("io.opentelemetry.instrumentation.javaagent-testing")
  id("io.opentelemetry.instrumentation.muzzle-check")
  id("io.opentelemetry.instrumentation.muzzle-generation")
}

dependencies {
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-semconv")
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-annotation-support")
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-appender-api-internal")
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")

  /*
    Dependencies added to this configuration will be found by the muzzle gradle plugin during code
    generation phase. These classes become part of the code that plugin inspects and traverses during
    references collection phase.
   */
  add("codegen", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")
}
