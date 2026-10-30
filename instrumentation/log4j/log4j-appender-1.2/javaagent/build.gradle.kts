import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("log4j")
    module.set("log4j")
    versions.set("[1.2,)")
    assertInverse.set(true)
  }
}

dependencies {
  // 1.2 introduces MDC and there's no version earlier than 1.2.4 available
  library("log4j:log4j:1.2.4")

  testInstrumentation(project(":instrumentation:log4j:log4j-appender-2.17:javaagent"))
}

configurations {
  // In order to test the real log4j library we need to remove the log4j transitive
  // dependency 'log4j-over-slf4j' brought in by :testing:instrumentation-testing which would shadow
  // the log4j module under test using a proxy to slf4j instead.
  testImplementation {
    exclude("org.slf4j", "log4j-over-slf4j")
  }
}

tasks {
  test {
    jvmArgs(
      "-Dotel.instrumentation.log4j-appender.experimental.mdc-attributes.included=key1,key2,exact,prefix.*,single?,excluded*,otel.event.name",
      "-Dotel.instrumentation.log4j-appender.experimental.mdc-attributes.excluded=prefix.secret,excluded*",
    )
  }
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          // TODO run tests both with and without experimental log attributes
          jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-code-attributes=true")
          jvmArgs("-Dotel.instrumentation.log4j-appender.experimental-log-attributes=true")
        }
        register("LegacyMdcAttributes") {
          filter.includeTestsMatching("*Log4jMdcSelectorTest")

          // the deprecated setting matches keys literally, so "*" alongside another entry does not
          // capture every key
          jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-mdc-attributes=*,legacy")
          systemProperty("testMdcConfiguration", "legacy")
        }
        register("MdcAttributeExclusionsOnly") {
          filter.includeTestsMatching("*Log4jMdcSelectorTest")

          jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.mdc-attributes.excluded=prefix.secret")
          systemProperty("testMdcConfiguration", "exclude-only")
        }
      }
    }
  }
}
