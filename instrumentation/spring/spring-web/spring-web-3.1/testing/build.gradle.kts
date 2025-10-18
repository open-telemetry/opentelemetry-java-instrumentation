plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testImplementation("org.springframework:spring-web:4.3.7.RELEASE")

  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))
}

tasks.withType<Test>().configureEach {
  systemProperty("otel.instrumentation.common.peer-service-mapping", "localhost=test-peer-service,127.0.0.1=test-peer-service,192.0.2.1=test-peer-service")
}
