plugins {
  id("otel.java-conventions")
}

// this module exists to make Intellij happy since it doesn't (currently at least) understand our
// inclusion of shaded dependencies into testing-common
// we use dependency substitution to replace io.opentelemetry.javaagent:opentelemetry-testing-common
// with this module
dependencies {
  api(project(":testing-common"))
  api(project(":testing:dependencies-shaded-for-testing", configuration = "shadow"))
}
