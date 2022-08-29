plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service-annotations")
  implementation("org.yaml:snakeyaml:1.31")
  testCompileOnly("com.google.auto.service:auto-service-annotations")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
}
