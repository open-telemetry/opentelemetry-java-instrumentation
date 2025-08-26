plugins {
  id("otel.java-conventions")
}

description = "smoke-tests-otel-starter-spring-boot-reactive-2"

dependencies {
  implementation(project(":instrumentation:spring:starters:spring-boot-starter"))
  val testLatestDeps = gradle.startParameter.projectProperties["testLatestDeps"] == "true"
  implementation(platform("org.springframework.boot:spring-boot-dependencies:" + if (testLatestDeps) "2.+" else "2.6.15"))

  implementation(project(":smoke-tests-otel-starter:spring-boot-reactive-common"))
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")

  runtimeOnly("com.h2database:h2")
  runtimeOnly("io.r2dbc:r2dbc-h2")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")
}

configurations.configureEach {
  resolutionStrategy {
    // our dependency management pins to a version that is not compatible with spring boot 2.7
    force("ch.qos.logback:logback-classic:1.2.13")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
