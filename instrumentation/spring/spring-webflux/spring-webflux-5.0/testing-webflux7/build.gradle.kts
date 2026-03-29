plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:spring:spring-core-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-webflux:spring-webflux-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-netty:reactor-netty-1.0:javaagent"))

  testImplementation(project(":instrumentation:spring:spring-webflux:spring-webflux-5.0:testing"))
  testImplementation(project(":instrumentation:spring:spring-webflux:spring-webflux-5.3:testing"))

  testImplementation("org.springframework.boot:spring-boot-starter-webflux:4.0.0")
  testImplementation("org.springframework:spring-web:7.0.0")
  testImplementation("org.springframework.boot:spring-boot-starter-test:4.0.0")
  testImplementation("org.springframework.boot:spring-boot-starter-reactor-netty:4.0.0")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
