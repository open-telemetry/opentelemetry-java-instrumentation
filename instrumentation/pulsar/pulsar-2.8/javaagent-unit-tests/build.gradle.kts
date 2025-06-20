plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:pulsar:pulsar-2.8:javaagent"))
  testImplementation(project(":instrumentation:pulsar:pulsar-common:javaagent"))
}
