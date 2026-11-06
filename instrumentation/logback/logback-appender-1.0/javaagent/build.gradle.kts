import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("ch.qos.logback")
    module.set("logback-classic")
    versions.set("[0.9.16,)")
    assertInverse.set(true)
  }
}

dependencies {
  // pin the version strictly to avoid overriding by dependencyManagement versions
  compileOnly("ch.qos.logback:logback-classic") {
    version {
      strictly("1.0.0")
    }
  }
  compileOnly("org.slf4j:slf4j-api") {
    version {
      strictly("1.5.8")
    }
  }

  if (otelProps.testLatestDeps) {
    testImplementation("ch.qos.logback:logback-classic:latest.release")
  } else {
    testImplementation("ch.qos.logback:logback-classic") {
      version {
        strictly("1.0.0")
      }
    }
    testImplementation("org.slf4j:slf4j-api") {
      version {
        strictly("1.7.36")
      }
    }
  }

  implementation(project(":instrumentation:logback:logback-appender-1.0:library"))

  testImplementation(project(":instrumentation:logback:logback-appender-1.0:testing"))
  testInstrumentation(project(":instrumentation:logback:logback-mdc-1.0:javaagent"))
}

testing.suites {
  register<JvmTestSuite>("mdcExportTest") {
    dependencies {
      implementation("ch.qos.logback:logback-classic") {
        version {
          strictly(baseVersion("1.0.0").orLatest())
        }
      }
      if (!otelProps.testLatestDeps) {
        implementation("org.slf4j:slf4j-api") {
          version {
            strictly("1.7.36")
          }
        }
      }
    }
  }

  executions("test") {
    configureDefault {
      jvmArgs("-Dotel.instrumentation.logback-appender.experimental.mdc-attributes.included=key?")
      jvmArgs("-Dotel.instrumentation.logback-appender.experimental.logger-context-attributes.included=key?")
    }

    register("MdcAttributeExclusionsOnly") {
      filter.includeTestsMatching("*LogbackMdcSelectorTest")
      jvmArgs("-Dotel.instrumentation.logback-appender.experimental.mdc-attributes.excluded=request-secret")
      systemProperty("testMdcConfiguration", "exclude-only")
    }

    register("LegacyMdcAttributes") {
      filter.includeTestsMatching("*LogbackMdcSelectorTest")
      // the deprecated setting matches keys literally, so "*" alongside another entry does not
      // capture every key
      jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-mdc-attributes=*,legacy")
      systemProperty("testMdcConfiguration", "legacy")
    }

    register("LegacyMdcAttributesCaptureAll") {
      filter.includeTestsMatching("*LogbackMdcSelectorTest")
      jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-mdc-attributes=*")
      systemProperty("testMdcConfiguration", "legacy-all")
    }

    register("LoggerContextAttributeExclusionsOnly") {
      filter.includeTestsMatching("*LogbackLoggerContextSelectorTest")
      jvmArgs("-Dotel.instrumentation.logback-appender.experimental.logger-context-attributes.excluded=request-secret")
      systemProperty("testLoggerContextConfiguration", "exclude-only")
    }

    register("LegacyLoggerContextAttributes") {
      filter.includeTestsMatching("*LogbackLoggerContextSelectorTest")
      jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-logger-context-attributes=true")
      systemProperty("testLoggerContextConfiguration", "legacy")
    }
  }

  register<JvmTestSuite>("slf4j2ApiTest") {
    dependencies {
      implementation("ch.qos.logback:logback-classic") {
        version {
          strictly(baseVersion("1.3.0").orLatest())
        }
      }
      implementation("org.slf4j:slf4j-api") {
        version {
          strictly(baseVersion("2.0.0").orLatest())
        }
      }
    }

    executions {
      configureDefault {
        jvmArgs(
          "-Dotel.instrumentation.common.logging.structured-attributes.included=key*",
          "-Dotel.instrumentation.common.logging.structured-attributes.excluded=*2",
        )
      }

      register("StructuredAttributesExcludedAll") {
        filter.includeTestsMatching("*LogbackKeyValuePairSelectorTest")
        jvmArgs("-Dotel.instrumentation.common.logging.structured-attributes.excluded=*")
        systemProperty("testKeyValuePairConfiguration", "none")
      }
    }
  }

  register<JvmTestSuite>("logstashMarkerTest") {
    dependencies {
      implementation(project(":instrumentation:logback:logback-appender-1.0:testing"))

      implementation("ch.qos.logback:logback-classic") {
        version {
          strictly(baseVersion("1.3.0").orLatest())
        }
      }
      implementation("org.slf4j:slf4j-api") {
        version {
          strictly(baseVersion("2.0.0").orLatest())
        }
      }
      implementation("net.logstash.logback:logstash-logback-encoder") {
        version {
          strictly(baseVersion("3.0").orLatest())
        }
      }
    }

    executions {
      configureDefault {
        jvmArgs("-Dotel.instrumentation.common.logging.structured-attributes.included=key?")
      }

      register("AttributeExclusionsOnly") {
        filter.includeTestsMatching("*LogbackLogstashMarkerSelectorTest")
        jvmArgs("-Dotel.instrumentation.common.logging.structured-attributes.excluded=other")
        systemProperty("testLogstashMarkerConfiguration", "exclude-only")
      }

      register("StructuredAttributesExcludedAll") {
        filter.includeTestsMatching("*LogbackLogstashMarkerSelectorTest")
        jvmArgs("-Dotel.instrumentation.common.logging.structured-attributes.excluded=*")
        systemProperty("testLogstashMarkerConfiguration", "none")
      }
    }
  }

  register<JvmTestSuite>("logstashStructuredArgsTest") {
    dependencies {
      implementation(project(":instrumentation:logback:logback-appender-1.0:testing"))

      implementation("ch.qos.logback:logback-classic") {
        version {
          strictly(baseVersion("1.3.0").orLatest())
        }
      }
      implementation("org.slf4j:slf4j-api") {
        version {
          strictly(baseVersion("2.0.0").orLatest())
        }
      }
      implementation("net.logstash.logback:logstash-logback-encoder") {
        version {
          strictly(baseVersion("6.6").orLatest())
        }
      }
    }

    executions {
      configureDefault {
        jvmArgs("-Dotel.instrumentation.common.logging.structured-attributes.included=key?")
      }

      register("AttributeExclusionsOnly") {
        filter.includeTestsMatching("*LogbackLogstashStructuredArgsSelectorTest")
        jvmArgs("-Dotel.instrumentation.common.logging.structured-attributes.excluded=other")
        systemProperty("testLogstashStructuredArgsConfiguration", "exclude-only")
      }

      register("StructuredAttributesExcludedAll") {
        filter.includeTestsMatching("*LogbackLogstashStructuredArgsSelectorTest")
        jvmArgs("-Dotel.instrumentation.common.logging.structured-attributes.excluded=*")
        systemProperty("testLogstashStructuredArgsConfiguration", "none")
      }
    }
  }
  executions("mdcExportTest") {
    configureEach {
      jvmArgs("-Dotel.instrumentation.logback-mdc.add-baggage=true")
      jvmArgs("-Dotel.instrumentation.common.mdc.resource-attributes=service.name")
      jvmArgs("-Dotel.instrumentation.logback-appender.experimental.mdc-attributes.included=*")
      jvmArgs("-Dotel.instrumentation.logback-appender.experimental.mdc-attributes.excluded=secret")
      jvmArgs("-Dotel.service.name=mdc-export-test")
    }

    register("LoggingKeys") {
      jvmArgs("-Dotel.instrumentation.common.logging.trace-id-key=custom_trace_id")
      jvmArgs("-Dotel.instrumentation.common.logging.span-id-key=custom_span_id")
      jvmArgs("-Dotel.instrumentation.common.logging.trace-flags-key=custom_trace_flags")
    }

    register("BaggageProcessor") {
      filter.includeTestsMatching("*exportsBaggageIndependently")
      systemProperty("test.baggageProcessor", "true")
      jvmArgs("-Dotel.instrumentation.logback-appender.experimental.mdc-attributes.excluded=*")
      jvmArgs("-Dotel.java.experimental.log-attributes.copy-from-baggage.include=tenant")
    }
  }
}

val mdcExportTestAgent = sourceSets.create("mdcExportTestAgent") {
  compileClasspath += configurations.getByName("mdcExportTestCompileClasspath")
}

dependencies {
  add(
    mdcExportTestAgent.compileOnlyConfigurationName,
    "io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi",
  )
  add("testInstrumentation", files(mdcExportTestAgent.output))
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental-log-attributes=true")
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-code-attributes=true")
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-marker-attribute=true")
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
