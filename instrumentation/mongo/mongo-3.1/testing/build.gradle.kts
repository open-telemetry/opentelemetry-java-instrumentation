plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":instrumentation:mongo:mongo-common:testing"))

  compileOnly("org.mongodb:mongo-java-driver:3.1.0")

  implementation("io.opentelemetry:opentelemetry-api")
}
