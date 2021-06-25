plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation("javax.jms:jms-api:1.1-rev-1")
  testImplementation(project(":instrumentation:jms-1.1:javaagent"))
  testImplementation(project(":instrumentation-api"))

  testImplementation("org.mockito:mockito-core")
  testImplementation("org.mockito:mockito-junit-jupiter")
}
