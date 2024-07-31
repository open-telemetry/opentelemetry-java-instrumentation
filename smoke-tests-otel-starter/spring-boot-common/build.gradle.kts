import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
  id("otel.java-conventions")
  id("org.springframework.boot") version "2.6.15"
}

description = "smoke-tests-otel-starter-spring-boot-common"

dependencies {
  // spring dependencies are compile only to enable testing against different versions of spring
  compileOnly(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
  compileOnly("org.springframework.boot:spring-boot-starter-web")
  compileOnly("org.springframework.boot:spring-boot-starter-test")
  compileOnly("org.springframework.boot:spring-boot-starter-data-jdbc")
  compileOnly("org.apache.commons:commons-dbcp2")
  compileOnly("org.springframework.kafka:spring-kafka")
  compileOnly("org.springframework.boot:spring-boot-starter-data-mongodb")
  compileOnly("org.testcontainers:junit-jupiter")
  compileOnly("org.testcontainers:kafka")
  compileOnly("org.testcontainers:mongodb")
  compileOnly("org.springframework.boot:spring-boot-starter-aop")

  api(project(":smoke-tests-otel-starter:spring-smoke-testing"))

  implementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
  implementation(project(":instrumentation:spring:starters:spring-boot-starter"))
}

tasks.withType<BootJar> {
  enabled = false
}
