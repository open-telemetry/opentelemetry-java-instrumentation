import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
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

testing.suites {
  register<JvmTestSuite>("testAddBaggage") {
    dependencies {
      implementation(project(":instrumentation:log4j:log4j-context-data:log4j-context-data-common:testing"))
    }
  }

  register<JvmTestSuite>("testLoggingKeys") {
    dependencies {
      implementation(project(":instrumentation:log4j:log4j-context-data:log4j-context-data-common:testing"))
    }
  }

  executions("test") {
    configureDefault {
      jvmArgs("-Dotel.instrumentation.common.mdc.resource-attributes=service.name,telemetry.sdk.language")
      // Threadlocals are always false if is.webapp is true, so we make sure to override it because as of
      // now testing-common includes jetty / servlet.
      jvmArgs("-Dlog4j2.enable.threadlocals=true")
    }
    register("PatternInjection") {
      filter { includeTestsMatching("*LogPatternTest") }
      systemProperty("otel.instrumentation.logging.pattern-injection.enabled", "true")
      systemProperty("otel.service.name", "pattern-service")
    }
    register("PatternInjectionCustomKeys") {
      filter { includeTestsMatching("*LogPatternTest") }
      environment("OTEL_INSTRUMENTATION_LOGGING_PATTERN_INJECTION_ENABLED", "true")
      environment("OTEL_SERVICE_NAME", "pattern-service")
      systemProperty("otel.instrumentation.common.logging.trace-id-key", "custom,trace")
      systemProperty("otel.instrumentation.common.logging.span-id-key", "custom,%span{}")
    }

    // Very different codepaths when threadlocals are enabled or not so we check both.
    // Regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2403
    register("DisableThreadLocals") {
      jvmArgs("-Dotel.instrumentation.common.mdc.resource-attributes=service.name,telemetry.sdk.language")
      jvmArgs("-Dlog4j2.enable.threadlocals=false")
    }
  }

  executions("testAddBaggage") {
    configureEach {
      jvmArgs("-Dotel.instrumentation.log4j-context-data.add-baggage=true")
      jvmArgs("-Dlog4j2.enable.threadlocals=true")
    }
  }

  executions("testLoggingKeys") {
    configureEach {
      jvmArgs("-Dotel.instrumentation.common.logging.trace-id-key=trace_id_test")
      jvmArgs("-Dotel.instrumentation.common.logging.span-id-key=span_id_test")
      jvmArgs("-Dotel.instrumentation.common.logging.trace-flags-key=trace_flags_test")
      jvmArgs("-Dlog4j2.enable.threadlocals=true")
    }
  }
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dlog4j2.is.webapp=false")
  }

  named("check") {
    dependsOn(testing.suites)
  }
}
