plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

dependencies {
  api("io.opentelemetry:opentelemetry-sdk")

  api("net.bytebuddy:byte-buddy")
  api("org.slf4j:slf4j-api")

  implementation(project(":instrumentation-api"))
  implementation(project(":javaagent-api"))
  // TODO: ideally this module should not depend on bootstrap, bootstrap should be an internal component
  implementation(project(":javaagent-bootstrap"))

  // metrics are unstable, do not expose as api
  implementation("io.opentelemetry:opentelemetry-sdk-metrics")
}
