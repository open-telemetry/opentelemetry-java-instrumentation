plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation("org.jodd:jodd-http:4.2.0")
  testImplementation(project(":instrumentation:jodd-http-4.2:javaagent"))
  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":instrumentation-api-incubator"))
}
