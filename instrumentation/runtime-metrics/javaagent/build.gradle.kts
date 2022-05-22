plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:runtime-metrics:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  compileOnly(project(":javaagent-tooling"))
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.runtime-metrics.experimental-metrics.enabled=true")
  }
}
