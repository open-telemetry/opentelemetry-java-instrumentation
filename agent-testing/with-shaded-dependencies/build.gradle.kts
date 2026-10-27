plugins {
  id("otel.java-conventions")
}

// Expose the embedded shared dependencies to IDEs, as in instrumentation-testing:with-shaded-dependencies.
dependencies {
  api(project(":agent-testing"))
  api(project(":instrumentation-testing:with-shaded-dependencies"))
}
