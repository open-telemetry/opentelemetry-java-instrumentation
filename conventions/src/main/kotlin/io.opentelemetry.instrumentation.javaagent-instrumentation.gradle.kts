plugins {
  id("io.opentelemetry.instrumentation.javaagent-testing")
  id("io.opentelemetry.instrumentation.muzzle-check")
  id("io.opentelemetry.instrumentation.muzzle-generation")
}

dependencies {
  compileOnly("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-api")
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-bootstrap")
  // Apply common dependencies for instrumentation.
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api") {
    // OpenTelemetry SDK is not needed for compilation
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk")
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk-metrics")
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk-logs")
  }
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-tooling") {
    // OpenTelemetry SDK is not needed for compilation
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk")
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk-metrics")
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk-logs")
  }

  // Used by byte-buddy but not brought in as a transitive dependency
  compileOnly("com.google.code.findbugs:annotations")

  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-annotation-support")
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-appender-api-internal")
  add("muzzleBootstrap", "io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-api")
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")
  add("muzzleTooling", "org.slf4j:slf4j-simple")

  /*
    Dependencies added to this configuration will be found by the muzzle gradle plugin during code
    generation phase. These classes become part of the code that plugin inspects and traverses during
    references collection phase.
   */
  add("codegen", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")
}
