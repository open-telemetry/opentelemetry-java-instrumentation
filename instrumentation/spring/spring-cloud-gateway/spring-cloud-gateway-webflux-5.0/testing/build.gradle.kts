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

  testLibrary("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux:5.0.0")
  testLibrary("org.springframework.boot:spring-boot-starter-test:4.0.0")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.spring-cloud-gateway.experimental-span-attributes=true")

  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}

// spring 7 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}
