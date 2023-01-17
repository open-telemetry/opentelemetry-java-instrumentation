plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  implementation("io.vertx:vertx-web:3.0.0")

  compileOnly("io.vertx:vertx-codegen:3.0.0")
  compileOnly("io.vertx:vertx-docgen:3.0.0")

  implementation("org.apache.groovy:groovy")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
