plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testImplementation("org.springframework:spring-web:4.3.7.RELEASE")

  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))
}
