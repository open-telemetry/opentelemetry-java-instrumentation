plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:geode-1.4:javaagent"))
}
