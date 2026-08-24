plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:elasticsearch:elasticsearch-transport-common-5.0:javaagent"))
}
