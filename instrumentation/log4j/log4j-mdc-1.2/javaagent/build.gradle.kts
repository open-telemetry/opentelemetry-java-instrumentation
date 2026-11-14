import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
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
}

configurations {
  // In order to test the real log4j library we need to remove the log4j transitive
  // dependency 'log4j-over-slf4j' brought in by :testing:testing-common which would shadow
  // the log4j module under test using a proxy to slf4j instead.
  testImplementation {
    exclude("org.slf4j", "log4j-over-slf4j")
  }

  // See: https://stackoverflow.com/a/9047963/2749853
  testImplementation {
    exclude("javax.jms", "jms")
  }
}

testing.suites {
  executions("test") {
    configureEach {
      systemProperty("testLatestDeps", otelProps.testLatestDeps)
    }
    configureDefault {
      jvmArgs("-Dotel.instrumentation.common.mdc.resource-attributes=service.name,telemetry.sdk.language")
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
      systemProperty("otel.instrumentation.common.logging.trace-id-key", "custom}trace")
      systemProperty("otel.instrumentation.common.logging.span-id-key", "custom%span")
    }
  }
}
