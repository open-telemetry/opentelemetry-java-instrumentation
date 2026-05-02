plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:jms:jms-common-1.1:javaagent"))
  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":instrumentation-api-incubator"))
}
