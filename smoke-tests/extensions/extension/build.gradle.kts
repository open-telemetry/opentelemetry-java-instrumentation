plugins {
  id("otel.java-conventions")
  id("io.opentelemetry.instrumentation.javaagent-instrumentation")
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")

  compileOnly("com.google.auto.service:auto-service-annotations")

  annotationProcessor("com.google.auto.service:auto-service")
}
