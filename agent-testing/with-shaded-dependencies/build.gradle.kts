plugins {
  id("otel.java-conventions")
}

// Expose the embedded shared dependencies to IDEs, as in testing-common:with-shaded-dependencies.
dependencies {
  api(project(":agent-testing"))
  api(project(":testing-common:with-shaded-dependencies"))
}
