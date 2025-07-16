plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:spring:spring-cloud-gateway:spring-cloud-gateway-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-netty:reactor-netty-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-webflux:spring-webflux-5.0:javaagent"))

  testImplementation(project(":instrumentation:spring:spring-cloud-gateway:spring-cloud-gateway-common:testing"))

  testLibrary("org.springframework.cloud:spring-cloud-starter-gateway:2.2.0.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter-test:2.2.0.RELEASE")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.spring-cloud-gateway.experimental-span-attributes=true")

  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")

  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

if (latestDepTest) {
  // spring 6 requires java 17
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
} else {
  // spring 5 requires old logback (and therefore also old slf4j)
  configurations.testRuntimeClasspath {
    resolutionStrategy {
      force("ch.qos.logback:logback-classic:1.2.11")
      force("org.slf4j:slf4j-api:1.7.36")
    }
  }
}
