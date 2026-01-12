plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  implementation("org.testcontainers:testcontainers-pulsar")

  compileOnly("org.springframework.pulsar:spring-pulsar:1.0.0")
  compileOnly("org.springframework.boot:spring-boot-starter-test:3.2.4")
  compileOnly("org.springframework.boot:spring-boot-starter:3.2.4")
}
