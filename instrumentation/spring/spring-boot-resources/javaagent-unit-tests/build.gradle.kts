plugins {
  id("otel.java-conventions")
}

dependencies {
  testCompileOnly("com.google.auto.service:auto-service-annotations")

  testImplementation("org.mockito:mockito-junit-jupiter")
  testImplementation(project(":instrumentation:spring:spring-boot-resources:javaagent"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  testImplementation(project(path = ":smoke-tests:images:spring-boot", configuration = "springBootJar"))
}
