plugins {
  id("otel.java-conventions")
  id("org.springframework.boot") version "2.7.18"
}

description = "smoke-tests-otel-starter-spring-boot-2"

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
  runtimeOnly("com.h2database:h2")
  implementation("org.apache.commons:commons-dbcp2")
  implementation("org.springframework.kafka:spring-kafka") // not tested here, just make sure there are no warnings when it's included
  implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

  implementation(project(":smoke-tests-otel-starter:spring-boot-common"))
  testImplementation("org.springframework.boot:spring-boot-starter-test")
}

springBoot {
  mainClass = "io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestApplication"
}

configurations.configureEach {
  resolutionStrategy {
    // our dependency management pins to a version that is not compatible with spring boot 2.7
    force("ch.qos.logback:logback-classic:1.2.13")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
