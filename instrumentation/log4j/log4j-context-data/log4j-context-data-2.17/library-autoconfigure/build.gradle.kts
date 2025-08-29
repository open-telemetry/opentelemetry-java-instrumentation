plugins {
  id("otel.library-instrumentation")
}

base.archivesName.set("${base.archivesName.get()}-autoconfigure")

dependencies {
  compileOnly(project(":javaagent-extension-api"))
  library("org.apache.logging.log4j:log4j-core:2.17.0")

  testImplementation(project(":instrumentation:log4j:log4j-context-data:log4j-context-data-common:testing"))
}

testing {
  suites {
    val testAddBaggage by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            filter {
              includeTestsMatching("LibraryLog4j2BaggageTest")
            }
            jvmArgs("-Dotel.instrumentation.log4j-context-data.add-baggage=true")
          }
        }
      }
    }

    val testLoggingKeys by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            filter {
              includeTestsMatching("LibraryLog4j2LoggingKeysTest")
            }
            jvmArgs("-Dotel.instrumentation.common.logging.trace-id=trace_id_test")
            jvmArgs("-Dotel.instrumentation.common.logging.span-id=span_id_test")
            jvmArgs("-Dotel.instrumentation.common.logging.trace-flags=trace_flags_test")
          }
        }
      }
    }
  }
}

tasks {
  test {
    filter {
      excludeTestsMatching("LibraryLog4j2BaggageTest")
      excludeTestsMatching("LibraryLog4j2LoggingKeysTest")
    }
  }

  named("check") {
    dependsOn(testing.suites)
  }
}
