plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-testing")

  compileOnly("org.springframework.boot:spring-boot-starter-web:1.5.17.RELEASE")
  compileOnly("org.springframework.boot:spring-boot-starter-security:1.5.17.RELEASE")
}
