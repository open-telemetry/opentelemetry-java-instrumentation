plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

dependencies {
  implementation(project(":instrumentation-api"))
  implementation("ch.qos.logback:logback-classic")

  testImplementation(project(":testing-common"))
}
