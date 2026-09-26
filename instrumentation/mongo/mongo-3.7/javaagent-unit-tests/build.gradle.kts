plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:mongo:mongo-3.1:javaagent"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation(project(":instrumentation:mongo:mongo-3.1:library"))
  testImplementation(project(":instrumentation-api"))
  testImplementation("org.mongodb:mongo-java-driver:3.11.0")
  testRuntimeOnly(project(":muzzle"))
}
