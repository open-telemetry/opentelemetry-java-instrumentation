plugins {
  id("otel.javaagent-instrumentation")
}

// This module's main code has been consolidated into :instrumentation:runtime-telemetry:javaagent
// Tests are kept to verify backward compatibility with legacy property names

dependencies {
  // used to verify jar analyzer package events
  testImplementation("commons-io:commons-io")
  testInstrumentation(project(":instrumentation:runtime-telemetry:javaagent"))
}

tasks {
  test {
    jvmArgs("-Dotel.instrumentation.runtime-telemetry.package-emitter.enabled=true")
  }
}
