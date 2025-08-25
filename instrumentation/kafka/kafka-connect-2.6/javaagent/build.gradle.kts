plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.kafka")
    module.set("connect-api")
    versions.set("[2.6.0,)")
    // we use reflection to access the "pause" and "resume" methods, so we can't reference them
    // directly, and so we can't assert that they exist at muzzle-time
    skip("org.apache.kafka.connect.sink.SinkTaskContext")
  }
}

dependencies {
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))

  library("org.apache.kafka:connect-api:2.6.0")

  testImplementation("org.apache.kafka:connect-runtime:2.6.0")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)

    // Enable experimental span attributes and receive telemetry for comprehensive testing
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    // Set timeout for integration tests with containers
    systemProperty("junit.jupiter.execution.timeout.default", "5m")
  }

  withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-deprecation")
  }
}
