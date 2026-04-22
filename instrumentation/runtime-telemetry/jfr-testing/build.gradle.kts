plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:runtime-telemetry:javaagent"))
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  test {
    jvmArgs("-Dotel.instrumentation.runtime-telemetry.emit-experimental-jfr-metrics=true")
  }
}
