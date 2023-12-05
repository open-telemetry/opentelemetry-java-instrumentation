plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:jms:jms-common:javaagent"))
  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":instrumentation-api-incubator"))
}
