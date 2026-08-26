plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:mongo:mongo-4.0:javaagent"))
  testImplementation(project(":instrumentation-api"))
  testImplementation("org.mongodb:mongodb-driver-core:4.0.1")
}
