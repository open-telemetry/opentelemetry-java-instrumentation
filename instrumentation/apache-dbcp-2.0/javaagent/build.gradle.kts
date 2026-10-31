import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.apache.commons")
    module.set("commons-dbcp2")
    versions.set("[2,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.commons:commons-dbcp2:2.0")

  implementation(project(":instrumentation:apache-dbcp-2.0:library"))
  implementation(project(":instrumentation:jdbc:javaagent-common"))

  bootstrap(project(":instrumentation:apache-commons-pool-2.0:bootstrap"))
  bootstrap(project(":instrumentation:jdbc:bootstrap"))

  testImplementation(project(":instrumentation:apache-dbcp-2.0:testing"))
  testInstrumentation(project(":instrumentation:apache-commons-pool-2.0:javaagent"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("WithCommonsPoolInstrumentation") {
          filter {
            includeTestsMatching(
              "ApacheDbcpInstrumentationTest.shouldNotReportCommonsPoolMetrics",
            )
          }

          jvmArgs("-Dotel.instrumentation.apache-commons-pool.enabled=true")
        }
      }
    }
  }
}
