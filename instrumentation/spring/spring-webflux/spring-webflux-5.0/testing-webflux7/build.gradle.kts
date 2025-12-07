plugins {
  id("otel.java-conventions")
  id("otel.javaagent-testing")
}

dependencies {
  // Register instrumentations with the test agent
  testInstrumentation(project(":instrumentation:spring:spring-core-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-webflux:spring-webflux-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-netty:reactor-netty-1.0:javaagent"))

  testImplementation(project(":instrumentation:spring:spring-webflux:spring-webflux-5.0:testing"))

  testImplementation("org.springframework.boot:spring-boot-starter-webflux:4.0.0")
  testImplementation("org.springframework.boot:spring-boot-starter-test:4.0.0")
  testImplementation("org.springframework.boot:spring-boot-starter-reactor-netty:4.0.0")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
