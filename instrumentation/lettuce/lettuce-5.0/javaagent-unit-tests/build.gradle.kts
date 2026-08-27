plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:lettuce:lettuce-5.0:javaagent"))
  testImplementation("io.lettuce:lettuce-core:5.0.0.RELEASE")
}
