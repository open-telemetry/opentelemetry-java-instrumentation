plugins {
  id("otel.javaagent-instrumentation")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation(project(":instrumentation:runtime-telemetry-jfr:library"))
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}

tasks {
  test {
    jvmArgs("-Dotel.instrumentation.runtime-telemetry-jfr.enabled=true")
  }
}
