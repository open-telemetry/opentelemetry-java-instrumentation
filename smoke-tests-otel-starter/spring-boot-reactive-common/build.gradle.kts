plugins {
  id("otel.java-conventions")
}

description = "smoke-tests-otel-starter-spring-boot-reactive-common"

dependencies {
  // spring dependencies are compile only to enable testing against different versions of spring
  compileOnly(platform("org.springframework.boot:spring-boot-dependencies:2.6.15"))
  compileOnly("org.springframework.boot:spring-boot-starter-web")
  compileOnly("org.springframework.boot:spring-boot-starter-webflux")
  compileOnly("org.springframework.boot:spring-boot-starter-test")
  compileOnly("org.springframework.boot:spring-boot-starter-data-r2dbc")
  compileOnly("org.springframework.cloud:spring-cloud-starter-gateway:3.1.8")
  api(project(":smoke-tests-otel-starter:spring-smoke-testing"))
}
