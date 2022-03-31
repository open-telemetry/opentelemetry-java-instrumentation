plugins {
  id("otel.java-conventions")
}

val versions: Map<String, String> by project

dependencies {
  api(project(":testing-common"))
  api("org.testcontainers:mongodb:${versions["org.testcontainers"]}")

  implementation("org.apache.groovy:groovy")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
