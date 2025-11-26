plugins {
  id("otel.java-conventions")
  alias(springBoot40.plugins.versions)
}

description = "smoke-tests-otel-starter-spring-boot-4"

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
  implementation("org.springframework.boot:spring-boot-starter-kafka")
  testImplementation("org.springframework:spring-aop")
  testImplementation("org.aspectj:aspectjweaver")
  runtimeOnly("com.h2database:h2")

  implementation(project(":smoke-tests-otel-starter:spring-boot-common"))

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-resttestclient")
  testImplementation(project(":instrumentation:spring:starters:spring-boot-starter"))
  testImplementation(project(":smoke-tests-otel-starter:spring-smoke-testing"))
  testImplementation("org.springframework.boot:spring-boot-starter-kafka")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.testcontainers:testcontainers-kafka")
}

springBoot {
  mainClass = "io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestApplication"
}

// Disable -Werror for Spring Framework 7.0 compatibility
tasks.withType<JavaCompile>().configureEach {
  options.compilerArgs.removeAll(listOf("-Werror"))
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
