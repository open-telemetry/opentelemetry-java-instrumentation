plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:spring:spring-boot-resources:javaagent"))
  testImplementation("io.opentelemetry:opentelemetry-api-incubator")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  testImplementation("io.opentelemetry.semconv:opentelemetry-semconv")
  testRuntimeOnly(project(path = ":smoke-tests:images:spring-boot", configuration = "springBootJar"))
}
