plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:runtime-metrics:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.runtime-metrics.experimental-metrics.enabled=true")
  }
}
