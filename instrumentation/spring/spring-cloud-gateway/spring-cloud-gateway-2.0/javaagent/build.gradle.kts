plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework.cloud")
    module.set("spring-cloud-starter-gateway")
    versions.set("[2.0.0.RELEASE,]")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.springframework.cloud:spring-cloud-starter-gateway:2.0.0.RELEASE")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-netty:reactor-netty-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-webflux:spring-webflux-5.0:javaagent"))

  testImplementation(project(":instrumentation:spring:spring-cloud-gateway:spring-cloud-gateway-common:testing"))

  testLibrary("org.springframework.boot:spring-boot-starter-test:2.0.0.RELEASE")

  latestDepTestLibrary("org.springframework.cloud:spring-cloud-starter-gateway:2.1.+") // see spring-cloud-gateway-2.2:testing module
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-test:2.1.+") // see spring-cloud-gateway-2.2:testing module
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.spring-cloud-gateway.experimental-span-attributes=true")

  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")

  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
