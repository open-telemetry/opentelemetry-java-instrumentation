plugins {
  id("otel.java-conventions")
  id("org.springframework.boot") version "3.5.4"
}

description = "smoke-tests-otel-starter-spring-boot-2"

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
  runtimeOnly("com.h2database:h2")
  implementation("org.apache.commons:commons-dbcp2")
  implementation("org.springframework.kafka:spring-kafka")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  implementation("org.springframework.boot:spring-boot-starter-aop")
  val testLatestDeps = gradle.startParameter.projectProperties["testLatestDeps"] == "true"
  implementation(platform("org.springframework.boot:spring-boot-dependencies:" + if (testLatestDeps) "2.+" else "2.6.15"))

  implementation(project(":smoke-tests-otel-starter:spring-boot-common"))

  testImplementation("org.testcontainers:junit-jupiter")
  testImplementation("org.testcontainers:kafka")
  testImplementation("org.testcontainers:mongodb")
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
