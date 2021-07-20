plugins {
  id("io.opentelemetry.instrumentation.javaagent-testing")
  id("io.opentelemetry.instrumentation.muzzle-check")
  id("io.opentelemetry.instrumentation.muzzle-generation")
}

dependencies {
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-annotation-support")
  add("muzzleBootstrap", "io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-api")
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")
  add("muzzleTooling", "org.slf4j:slf4j-simple")

  add("codegen", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")
}
