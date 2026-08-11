plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:logback:logback-appender-1.0:javaagent"))
}
