plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation-api"))

  testImplementation(project(":testing-common"))
}

testing {
  suites {
    val testStableSemconv by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation(project(":testing-common"))
      }
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.opt-in=jvm")
          }
        }
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
