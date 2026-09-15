plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  bootstrap(project(":instrumentation:jms:jms-common-1.1:bootstrap"))
}

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation:jms:jms-common-1.1:bootstrap"))
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
