plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  api("org.testcontainers:mongodb")

  implementation("org.apache.groovy:groovy")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}