import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
  id("otel.java-conventions")
  id("org.springframework.boot") version "2.6.15"
}

description = "smoke-tests-otel-starter-spring-boot-reactive-common"

dependencies {
  // spring dependencies are compile only to enable testing against different versions of spring
  compileOnly(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
  compileOnly("org.springframework.boot:spring-boot-starter-web")
  compileOnly("org.springframework.boot:spring-boot-starter-webflux")
  compileOnly("org.springframework.boot:spring-boot-starter-test")
  compileOnly("org.springframework.boot:spring-boot-starter-data-r2dbc")
  api(project(":smoke-tests-otel-starter:spring-smoke-testing"))
}

tasks.withType<BootJar> {
  enabled = false
}
