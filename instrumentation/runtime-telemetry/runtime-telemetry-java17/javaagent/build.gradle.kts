plugins {
  id("otel.javaagent-instrumentation")
}

// This module's main code has been consolidated into :instrumentation:runtime-telemetry:javaagent
// Tests are kept to verify backward compatibility

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  testInstrumentation(project(":instrumentation:runtime-telemetry:javaagent"))
}

tasks {
  test {
    jvmArgs("-Dotel.instrumentation.runtime-telemetry-java17.enabled=true")
  }
}
