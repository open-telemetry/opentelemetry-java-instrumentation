plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "OpenTelemetry Javaagent instrumentation testing"
group = "io.opentelemetry.javaagent"
base.archivesName.set("opentelemetry-javaagent-instrumentation-testing")

dependencies {
  api(project(":instrumentation-testing"))

  compileOnly(project(":javaagent-bootstrap"))
  compileOnly(project(":testing:dependencies-shaded-for-testing", configuration = "shadow"))

  implementation("ch.qos.logback:logback-classic")
}

tasks.javadoc {
  enabled = false
}
