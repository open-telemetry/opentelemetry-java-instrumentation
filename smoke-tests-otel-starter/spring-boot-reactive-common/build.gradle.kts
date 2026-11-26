plugins {
  id("otel.java-conventions")
}

description = "smoke-tests-otel-starter-spring-boot-reactive-common"

dependencies {
  // spring dependencies are compile only to enable testing against different versions of spring
  compileOnly(platform(libs.spring.boot2.bom))
  compileOnly("org.springframework.boot:spring-boot-starter-web")
  compileOnly("org.springframework.boot:spring-boot-starter-webflux")
  compileOnly("org.springframework.boot:spring-boot-starter-test")
  compileOnly("org.springframework.boot:spring-boot-starter-data-r2dbc")
  compileOnly(libs.spring.cloud.gateway.boot2)
  api(project(":smoke-tests-otel-starter:spring-smoke-testing"))
}
