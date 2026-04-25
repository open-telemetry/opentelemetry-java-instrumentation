plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:spring:spring-boot-resources:javaagent"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  testRuntimeOnly(project(path = ":smoke-tests:images:spring-boot", configuration = "springBootJar"))
}
