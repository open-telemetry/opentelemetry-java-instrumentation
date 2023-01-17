plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("io.lettuce:lettuce-core:5.1.0.RELEASE")

  implementation("org.testcontainers:testcontainers")
  implementation("com.google.guava:guava")

  implementation("org.apache.groovy:groovy")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
