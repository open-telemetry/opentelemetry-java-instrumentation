plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:kubernetes-client-7.0:javaagent"))
  testImplementation("io.kubernetes:client-java-api:7.0.0")
}
