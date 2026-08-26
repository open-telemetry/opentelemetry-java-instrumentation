plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:mongo:mongo-3.7:javaagent"))
  testImplementation(project(":instrumentation-api"))
  testImplementation("org.mongodb:mongo-java-driver:3.11.0")
}
