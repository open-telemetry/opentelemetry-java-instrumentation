plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("org.vibur:vibur-dbcp:11.0")

  testImplementation(project(":instrumentation:vibur-dbcp-11.0:testing"))
}

testing {
  suites {
    val testStableSemconv by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.opt-in=database")
          }
        }
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites.named("testStableSemconv"))
  }
}
