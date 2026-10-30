plugins {
  id("otel.java-conventions")
}

// this module exists to make Intellij happy since it doesn't (currently at least) understand our
// inclusion of shaded dependencies into instrumentation-testing
// we use dependency substitution to replace io.opentelemetry.javaagent:opentelemetry-instrumentation-testing
// with this module
dependencies {
  api(project(":testing:instrumentation-testing"))
  api(project(":testing:shaded-dependencies", configuration = "shadow"))
}
