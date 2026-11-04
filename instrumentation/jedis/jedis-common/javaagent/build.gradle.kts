plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
