plugins {
  id("otel.java-conventions")
}

description = "smoke-tests-otel-starter-spring-boot-2"

val testLatestDeps = gradle.startParameter.projectProperties["testLatestDeps"] == "true"
val springBootVersion = if (testLatestDeps) "2.+" else "2.6.15"

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  runtimeOnly("com.h2database:h2")
  implementation("org.apache.commons:commons-dbcp2")
  implementation("org.springframework.kafka:spring-kafka")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  implementation("org.springframework.boot:spring-boot-starter-aop")
  implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))

  implementation(project(":smoke-tests-otel-starter:spring-boot-common"))

  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.testcontainers:testcontainers-kafka")
  testImplementation("org.testcontainers:testcontainers-mongodb")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
}

configurations.configureEach {
  resolutionStrategy {
    // our dependency management pins to a version that is not compatible with spring boot 2.7
    force("ch.qos.logback:logback-classic:1.2.13")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}

testing {
  suites {
    val testDeclarativeConfig by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation(project(":smoke-tests-otel-starter:spring-boot-common"))
        implementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
        implementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
