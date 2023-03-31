plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:apache-pulsar:apache-pulsar-2.8:javaagent"))
}
