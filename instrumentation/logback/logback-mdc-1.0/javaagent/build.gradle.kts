import io.opentelemetry.instrumentation.gradle.executions

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

testing.suites {
  register<JvmTestSuite>("addBaggageTest")
  register<JvmTestSuite>("loggingKeysTest")

  withType(JvmTestSuite::class) {
    dependencies {
      implementation("ch.qos.logback:logback-classic") {
        version {
          strictly(baseVersion("1.0.0").orLatest())
        }
      }
      if (!otelProps.testLatestDeps) {
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

  executions("test") {
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
      systemProperty("otel.instrumentation.common.logging.trace-id-key", "custom,'\"trace\\")
      systemProperty("otel.instrumentation.common.logging.span-id-key", "custom:-span%('\"\\)")
    }
  }

  executions("addBaggageTest") {
    configureEach {
      jvmArgs("-Dotel.instrumentation.logback-mdc.add-baggage=true")
    }
  }

  executions("loggingKeysTest") {
    configureEach {
      jvmArgs("-Dotel.instrumentation.common.logging.trace-id-key=trace_id_test")
      jvmArgs("-Dotel.instrumentation.common.logging.span-id-key=span_id_test")
      jvmArgs("-Dotel.instrumentation.common.logging.trace-flags-key=trace_flags_test")
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
  named("check") {
    dependsOn(testing.suites)
  }
}
