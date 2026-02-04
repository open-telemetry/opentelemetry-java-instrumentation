plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:runtime-telemetry:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  compileOnly("io.opentelemetry:opentelemetry-api-incubator")
}

tasks {
  test {
    jvmArgs("-Dotel.instrumentation.runtime-telemetry.experimental.package-emitter.enabled=true")
  }
}
