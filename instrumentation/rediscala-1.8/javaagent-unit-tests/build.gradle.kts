plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:rediscala-1.8:javaagent"))
  testImplementation("com.github.etaty:rediscala_2.11:1.8.0")
}
