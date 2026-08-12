plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:log4j:log4j-appender-2.17:javaagent"))
}
