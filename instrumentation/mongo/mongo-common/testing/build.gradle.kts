plugins {
  id("otel.java-conventions")
}

val versions: Map<String, String> by project

dependencies {
  api(project(":testing-common"))
  api("org.testcontainers:mongodb:${versions["org.testcontainers"]}")

  implementation("org.codehaus.groovy:groovy-all")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
