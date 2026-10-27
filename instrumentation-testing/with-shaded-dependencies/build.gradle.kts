plugins {
  id("otel.java-conventions")
}

// this module exists to make Intellij happy since it doesn't (currently at least) understand our
// inclusion of shaded dependencies into instrumentation-testing
// we use dependency substitution to replace io.opentelemetry.instrumentation:opentelemetry-instrumentation-testing
// with this module
dependencies {
  api(project(":instrumentation-testing"))
  api(project(":testing:dependencies-shaded-for-testing", configuration = "shadow"))
}
