plugins {
  id("otel.java-conventions")
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  compileOnly("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
  compileOnly("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-incubator")
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")

  compileOnly("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service-annotations")

  annotationProcessor("com.google.auto.service:auto-service")

  // Used by byte-buddy but not brought in as a transitive dependency
  compileOnly("com.google.code.findbugs:annotations")
}
