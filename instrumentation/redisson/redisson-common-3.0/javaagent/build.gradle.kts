plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("org.redisson:redisson:3.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation-api-incubator"))
        implementation("org.redisson:redisson:3.0.0")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
