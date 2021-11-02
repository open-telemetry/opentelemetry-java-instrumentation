plugins {
  id("otel.javaagent-instrumentation")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_1_9)
}
