plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  implementation("org.apache.groovy:groovy")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
  implementation("org.testcontainers:testcontainers")

  compileOnly("org.redisson:redisson:3.0.0")
}
