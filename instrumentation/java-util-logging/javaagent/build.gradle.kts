import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  compileOnly(project(":instrumentation:java-util-logging:shaded-stub-for-instrumenting"))

  // ensure no cross interference
  testInstrumentation(project(":instrumentation:jboss-logmanager:jboss-logmanager-appender-1.1:javaagent"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("Experimental") {
          jvmArgs("-Dotel.instrumentation.java-util-logging.experimental-log-attributes=true")
        }
        register("CaptureTemplateAndArguments") {
          jvmArgs(
            "-Dotel.instrumentation.java-util-logging.experimental.capture-template=true",
            "-Dotel.instrumentation.java-util-logging.experimental.capture-arguments=true",
          )
        }
      }
    }
  }
}
