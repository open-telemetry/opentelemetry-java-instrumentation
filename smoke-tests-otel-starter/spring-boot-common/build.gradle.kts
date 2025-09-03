plugins {
  id("otel.java-conventions")
}

description = "smoke-tests-otel-starter-spring-boot-common"

dependencies {
  // spring dependencies are compile only to enable testing against different versions of spring
  compileOnly(platform("org.springframework.boot:spring-boot-dependencies:2.6.15"))
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

tasks {
  compileJava {
    with(options) {
      // preserve parameter names for @SpanAttribute
      compilerArgs.add("-parameters")
    }
  }
}
