plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  compileOnly("org.springframework:spring-webflux:7.0.0")
}
