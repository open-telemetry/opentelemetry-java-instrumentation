import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.jboss.logmanager")
    module.set("jboss-logmanager")
    versions.set("[1.1.0.GA,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.jboss.logmanager:jboss-logmanager:1.1.0.GA")

  // ensure no cross interference
  testInstrumentation(project(":instrumentation:java-util-logging:javaagent"))
}

if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}

tasks {
  test {
    jvmArgs(
      "-Dotel.instrumentation.jboss-logmanager.experimental.mdc-attributes.included=exact,prefix.*,single?,excluded*,otel.event.name",
      "-Dotel.instrumentation.jboss-logmanager.experimental.mdc-attributes.excluded=prefix.secret,excluded*",
    )
    systemProperty("testMdcConfiguration", "new")
  }
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          // TODO run tests both with and without experimental log attributes
          jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental-log-attributes=true")
          jvmArgs("-Dotel.instrumentation.java-util-logging.experimental-log-attributes=true")
        }
        register("CaptureTemplateAndArguments") {
          jvmArgs(
            "-Dotel.instrumentation.jboss-logmanager.experimental.capture-template=true",
            "-Dotel.instrumentation.jboss-logmanager.experimental.capture-arguments=true",
          )
        }
        register("LegacyMdcAttributes") {
          jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes=legacy")
          systemProperty("testMdcConfiguration", "legacy")
        }
        register("ExcludedOnlyMdcAttributes") {
          filter {
            includeTestsMatching("JbossLogmanagerTest.testMdc")
          }

          jvmArgs("-Dotel.instrumentation.jboss-logmanager.experimental.mdc-attributes.excluded=prefix.secret,excluded*")
          systemProperty("testMdcConfiguration", "excludedOnly")
        }
      }
    }
  }
}
