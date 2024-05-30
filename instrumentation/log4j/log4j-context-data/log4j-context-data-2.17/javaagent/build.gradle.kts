plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.logging.log4j")
    module.set("log4j-core")
    versions.set("[2.17.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.logging.log4j:log4j-core:2.17.0")

  implementation(project(":instrumentation:log4j:log4j-context-data:log4j-context-data-2.17:library-autoconfigure"))

  testInstrumentation(project(":instrumentation:log4j:log4j-context-data:log4j-context-data-2.7:javaagent"))

  testImplementation(project(":instrumentation:log4j:log4j-context-data:log4j-context-data-common:testing"))
}

testing {
  suites {
    // Very different codepaths when threadlocals are enabled or not so we check both.
    // Regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2403
    val testDisableThreadLocals by registering(JvmTestSuite::class) {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }
      dependencies {
        implementation(project(":instrumentation:log4j:log4j-context-data:log4j-context-data-common:testing"))
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dlog4j2.is.webapp=false")
            jvmArgs("-Dlog4j2.enable.threadlocals=false")
            jvmArgs("-Dotel.instrumentation.common.mdc.resource-attributes=service.name,telemetry.sdk.language")
          }
        }
      }
    }

    val testAddBaggage by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:log4j:log4j-context-data:log4j-context-data-common:testing"))
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.instrumentation.log4j-context-data.add-baggage=true")
            jvmArgs("-Dlog4j2.is.webapp=false")
            jvmArgs("-Dlog4j2.enable.threadlocals=true")
          }
        }
      }
    }

    val testLoggingKeys by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:log4j:log4j-context-data:log4j-context-data-common:testing"))
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.instrumentation.common.logging.trace-id=trace_id_test")
            jvmArgs("-Dotel.instrumentation.common.logging.span-id=span_id_test")
            jvmArgs("-Dotel.instrumentation.common.logging.trace-flags=trace_flags_test")
            jvmArgs("-Dlog4j2.is.webapp=false")
            jvmArgs("-Dlog4j2.enable.threadlocals=true")
          }
        }
      }
    }
  }
}

tasks {
  // Threadlocals are always false if is.webapp is true, so we make sure to override it because as of
  // now testing-common includes jetty / servlet.
  test {
    jvmArgs("-Dlog4j2.is.webapp=false")
    jvmArgs("-Dlog4j2.enable.threadlocals=true")
    jvmArgs("-Dotel.instrumentation.common.mdc.resource-attributes=service.name,telemetry.sdk.language")
  }

  named("check") {
    dependsOn(testing.suites)
  }
}
