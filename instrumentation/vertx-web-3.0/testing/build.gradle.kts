plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  implementation("io.vertx:vertx-web:3.0.0")

  implementation("org.codehaus.groovy:groovy-all")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
