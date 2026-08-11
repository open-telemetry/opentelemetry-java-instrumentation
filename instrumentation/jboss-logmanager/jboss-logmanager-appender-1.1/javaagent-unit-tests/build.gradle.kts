plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(
    project(":instrumentation:jboss-logmanager:jboss-logmanager-appender-1.1:javaagent"),
  )
}
