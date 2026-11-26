plugins {
  id("otel.java-conventions")
}

description = "smoke-tests-otel-starter-spring-smoke-testing"

dependencies {
  // spring dependencies are compile only to enable testing against different versions of spring
  compileOnly(platform(libs.spring.boot2.bom))
  compileOnly("org.springframework.boot:spring-boot-starter")
  compileOnly("org.springframework.boot:spring-boot-starter-test")
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")
  api("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
}
