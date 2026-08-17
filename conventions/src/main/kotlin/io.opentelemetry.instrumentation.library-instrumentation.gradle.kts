plugins {
  id("io.opentelemetry.instrumentation.base")
}

dependencies {
  api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
  // this is intentionally not api, so that it is not exposed on the compile classpath of consumers
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-incubator")

  api("io.opentelemetry:opentelemetry-api")

  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
}

testing {
  suites.withType(JvmTestSuite::class).configureEach {
    dependencies {
      implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
    }
  }
}
