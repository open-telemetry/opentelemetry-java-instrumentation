plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("ch.qos.logback")
    module.set("logback-classic")
    versions.set("[1.0.0,1.2.3]")
  }
}

testing {
  suites {
    val addBaggageTest by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.instrumentation.logback-mdc.add-baggage=true")
          }
        }
      }
    }

    val loggingKeysTest by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.instrumentation.common.logging.trace-id=trace_id_test")
            jvmArgs("-Dotel.instrumentation.common.logging.span-id=span_id_test")
            jvmArgs("-Dotel.instrumentation.common.logging.trace-flags=trace_flags_test")
          }
        }
      }
    }

    withType(JvmTestSuite::class) {
      dependencies {
        if (findProperty("testLatestDeps") as Boolean) {
          implementation("ch.qos.logback:logback-classic:latest.release")
        } else {
          implementation("ch.qos.logback:logback-classic") {
            version {
              strictly("1.0.0")
            }
          }
          implementation("org.slf4j:slf4j-api") {
            version {
              strictly("1.6.4")
            }
          }
        }

        implementation(project(":instrumentation:logback:logback-mdc-1.0:testing"))
        implementation(project(":instrumentation:logback:logback-mdc-1.0:javaagent"))
      }
    }
  }
}

dependencies {
  implementation(project(":instrumentation:logback:logback-mdc-1.0:library"))

  // pin the version strictly to avoid overriding by dependencyManagement versions
  compileOnly("ch.qos.logback:logback-classic") {
    version {
      strictly("1.0.0")
    }
  }
  compileOnly("org.slf4j:slf4j-api") {
    version {
      strictly("1.6.4")
    }
  }
}

tasks {
  test {
    jvmArgs("-Dotel.instrumentation.common.mdc.resource-attributes=service.name,telemetry.sdk.language")
  }

  named("check") {
    dependsOn(testing.suites)
  }
}
