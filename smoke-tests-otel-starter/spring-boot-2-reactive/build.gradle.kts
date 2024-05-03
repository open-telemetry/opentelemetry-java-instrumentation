plugins {
  id("otel.java-conventions")
  id("org.springframework.boot") version "2.7.18"
}

description = "smoke-tests-otel-starter-spring-boot-2-reactive"

dependencies {
  implementation(project(":instrumentation:spring:starters:spring-boot-starter"))
  implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

  implementation(project(":smoke-tests-otel-starter:spring-smoke-testing"))
  implementation("org.springframework.boot:spring-boot-starter-webflux")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")
}

springBoot {
  mainClass = "io.opentelemetry.spring.smoketest.OtelReactiveSpringStarterSmokeTestApplication"
}

configurations.configureEach {
  resolutionStrategy {
    // our dependency management pins to a version that is not compatible with spring boot 2.7
    force("ch.qos.logback:logback-classic:1.2.13")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}

tasks {
  test {
    // suppress warning about byte-buddy-agent being loaded dynamically
    jvmArgs("-XX:+EnableDynamicAgentLoading")
  }
}
