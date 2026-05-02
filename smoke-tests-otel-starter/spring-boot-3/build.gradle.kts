plugins {
  id("otel.java-conventions")
  alias(springBoot31.plugins.versions)
  id("otel.spring-native-test-conventions")
}

description = "smoke-tests-otel-starter-spring-boot-3"

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
  runtimeOnly("com.h2database:h2")
  implementation("org.apache.commons:commons-dbcp2")
  implementation("org.springframework.kafka:spring-kafka")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  implementation("org.springframework.boot:spring-boot-starter-aop")
  implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

  implementation(project(":smoke-tests-otel-starter:spring-boot-common"))
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.testcontainers:testcontainers-kafka")
  testImplementation("org.testcontainers:testcontainers-mongodb")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation(project(":instrumentation:spring:spring-boot-autoconfigure"))

  if (otelProps.testLatestDeps) {
    // with spring boot 3.5.0 versions of org.mongodb:mongodb-driver-sync and org.mongodb:mongodb-driver-core
    // are not in sync
    testImplementation("org.mongodb:mongodb-driver-sync:latest.release")
  }
}

springBoot {
  mainClass = "io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestApplication"
}

tasks {
  bootJar {
    enabled = false
  }
}
