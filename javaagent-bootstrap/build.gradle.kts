plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

dependencies {
  implementation(project(":instrumentation-api"))
  implementation(project(":instrumentation-appender-api-internal"))
  implementation("org.slf4j:slf4j-api")
  implementation("org.slf4j:slf4j-simple")

  testImplementation(project(":testing-common"))
}
