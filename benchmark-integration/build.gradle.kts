plugins {
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
}

description = "Integration Level Agent benchmarks."

subprojects {
  tasks {
    plugins.withId("java") {
      named("javadoc") {
        enabled = false
      }
    }
  }
}
