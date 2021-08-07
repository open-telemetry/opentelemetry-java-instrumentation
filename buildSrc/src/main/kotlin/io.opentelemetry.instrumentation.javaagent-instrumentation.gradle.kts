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

  /*
    Dependencies added to this configuration will be found by the muzzle gradle plugin during code
    generation phase. They differ from the implementation dependencies declared in plugin's build
    script, because these classes become part of the code that plugin inspects and traverses during
    references collection phase. They are not part of the observer, they are part of the observation.
   */
  add("codegen", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")
}
