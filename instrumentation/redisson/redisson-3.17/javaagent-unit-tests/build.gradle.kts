plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:redisson:redisson-3.17:javaagent"))
  // a version from the window where redisson routes the configuration through a service manager
  testImplementation("org.redisson:redisson:3.24.3")
}
