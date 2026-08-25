plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:redisson:redisson-3.0:javaagent"))
  testImplementation("org.redisson:redisson:3.0.0")
}
