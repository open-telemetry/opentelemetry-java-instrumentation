import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
}

base.archivesName.set("${base.archivesName.get()}-autoconfigure")

dependencies {
  compileOnly(project(":javaagent-extension-api"))
  library("org.apache.logging.log4j:log4j-core:2.17.0")

  testImplementation(project(":instrumentation:log4j:log4j-context-data:log4j-context-data-common:testing"))
}

testing.suites {
  executions("test") {
    configureDefault {
      filter {
        excludeTestsMatching("LibraryLog4j2BaggageTest")
        excludeTestsMatching("LibraryLog4j2LoggingKeysTest")
      }
    }
    register("AddBaggage") {
      filter {
        includeTestsMatching("LibraryLog4j2BaggageTest")
      }
      jvmArgs("-Dotel.instrumentation.log4j-context-data.add-baggage=true")
    }
    register("LoggingKeys") {
      filter {
        includeTestsMatching("LibraryLog4j2LoggingKeysTest")
      }
      jvmArgs("-Dotel.instrumentation.common.logging.trace-id-key=trace_id_test")
      jvmArgs("-Dotel.instrumentation.common.logging.span-id-key=span_id_test")
      jvmArgs("-Dotel.instrumentation.common.logging.trace-flags-key=trace_flags_test")
    }
  }
}
