plugins {
  id("otel.sdk-extension")
  id("otel.nullaway-conventions")
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")

  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service-annotations")
  testCompileOnly("com.google.auto.service:auto-service-annotations")

  implementation("org.snakeyaml:snakeyaml-engine")

  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  testImplementation("io.opentelemetry:opentelemetry-api-incubator")
  testImplementation("io.opentelemetry.semconv:opentelemetry-semconv")
  testRuntimeOnly(project(path = ":smoke-tests:images:spring-boot", configuration = "springBootJar"))
}
