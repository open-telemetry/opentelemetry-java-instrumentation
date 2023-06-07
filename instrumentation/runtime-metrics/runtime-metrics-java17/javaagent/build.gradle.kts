plugins {
  id("otel.javaagent-instrumentation")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation(project(":instrumentation:runtime-metrics:runtime-metrics-java17:library"))
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}

tasks {
  test {
    jvmArgs("-Dotel.instrumentation.runtime-metrics-java17.enabled=true")
  }
}
