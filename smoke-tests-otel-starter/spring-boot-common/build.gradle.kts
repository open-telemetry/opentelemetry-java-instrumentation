import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
  id("io.spring.dependency-management") version "1.1.6"
  id("otel.java-conventions")
  id("org.springframework.boot") version "2.6.15"
}

description = "smoke-tests-otel-starter-spring-boot-common"

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:2.6.15")
  }
}

dependencies {
  // spring dependencies are compile only to enable testing against different versions of spring
  compileOnly("org.springframework.boot:spring-boot-starter-web")
  compileOnly("org.springframework.boot:spring-boot-starter-test")
  compileOnly("org.springframework.boot:spring-boot-starter-data-jdbc")
  compileOnly("org.apache.commons:commons-dbcp2")
  compileOnly("org.springframework.kafka:spring-kafka")
  compileOnly("org.springframework.boot:spring-boot-starter-data-mongodb")
  compileOnly("org.testcontainers:junit-jupiter")
  compileOnly("org.testcontainers:kafka")
  compileOnly("org.testcontainers:mongodb")

  api(project(":smoke-tests-otel-starter:spring-smoke-testing"))

  implementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
  implementation(project(":instrumentation:spring:starters:spring-boot-starter"))
}

tasks.withType<BootJar> {
  enabled = false
}
