plugins {
  id("io.spring.dependency-management") version "1.1.6"
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:2.6.15")
  }
}

dependencies {
  api("org.springframework.boot:spring-boot-starter")
  api(project(":instrumentation:spring:starters:spring-boot-starter"))
  api("io.opentelemetry:opentelemetry-exporter-zipkin")
}
