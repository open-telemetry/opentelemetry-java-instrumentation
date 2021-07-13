plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

dependencies {
  api("io.opentelemetry:opentelemetry-api")
  api("io.opentelemetry:opentelemetry-api-metrics")

  compileOnly("io.opentelemetry:opentelemetry-sdk")

  implementation(project(":instrumentation-api"))
  implementation(project(":javaagent-instrumentation-api"))

  implementation("org.slf4j:slf4j-api")
  implementation("org.slf4j:slf4j-simple")
  // ^ Generally a bad idea for libraries, but we're shadowing.

  testImplementation(project(":testing-common"))
  testImplementation("org.mockito:mockito-core")
  testImplementation("org.assertj:assertj-core")
}
