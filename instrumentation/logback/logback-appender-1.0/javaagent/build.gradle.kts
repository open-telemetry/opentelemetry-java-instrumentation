plugins {
  id("otel.javaagent-instrumentation")
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
}

testing {
  suites {
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

      targets {
        all {
          testTask.configure {
            jvmArgs(
              "-Dotel.instrumentation.logback-appender.experimental.key-value-pair-attributes.included=key*",
              "-Dotel.instrumentation.logback-appender.experimental.key-value-pair-attributes.excluded=*2",
            )
          }
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
    }
  }
}

tasks {
  test {
    jvmArgs("-Dotel.instrumentation.logback-appender.experimental.mdc-attributes.included=key?")
    jvmArgs("-Dotel.instrumentation.logback-appender.experimental.logger-context-attributes.included=key?")
  }

  val slf4j2ApiTestSourceSet = sourceSets.named("slf4j2ApiTest")

  val testLegacyKeyValuePairAttributes = register<Test>("testLegacyKeyValuePairAttributes") {
    testClassesDirs = slf4j2ApiTestSourceSet.get().output.classesDirs
    classpath = slf4j2ApiTestSourceSet.get().runtimeClasspath

    jvmArgs(
      "-Dotel.instrumentation.logback-appender.experimental.capture-key-value-pair-attributes=true"
    )
    systemProperty("testKeyValuePairConfiguration", "legacy")
  }

  val testMdcAttributeExclusionsOnly = register<Test>("testMdcAttributeExclusionsOnly") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*LogbackMdcSelectorTest")

    jvmArgs("-Dotel.instrumentation.logback-appender.experimental.mdc-attributes.excluded=request-secret")
    systemProperty("testMdcConfiguration", "exclude-only")
  }

  val testLegacyMdcAttributes = register<Test>("testLegacyMdcAttributes") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*LogbackMdcSelectorTest")

    // the deprecated setting matches keys literally, so "*" alongside another entry does not
    // capture every key
    jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-mdc-attributes=*,legacy")
    systemProperty("testMdcConfiguration", "legacy")
  }

  val testLegacyMdcAttributesCaptureAll = register<Test>("testLegacyMdcAttributesCaptureAll") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*LogbackMdcSelectorTest")

    jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-mdc-attributes=*")
    systemProperty("testMdcConfiguration", "legacy-all")
  }

  val testMdcAttributePrecedence = register<Test>("testMdcAttributePrecedence") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*LogbackMdcSelectorTest")

    jvmArgs(
      "-Dotel.instrumentation.logback-appender.experimental.mdc-attributes.included=new",
      "-Dotel.instrumentation.logback-appender.experimental.capture-mdc-attributes=legacy",
    )
    systemProperty("testMdcConfiguration", "precedence")
  }

  val testLoggerContextAttributeExclusionsOnly = register<Test>("testLoggerContextAttributeExclusionsOnly") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*LogbackLoggerContextSelectorTest")

    jvmArgs("-Dotel.instrumentation.logback-appender.experimental.logger-context-attributes.excluded=request-secret")
    systemProperty("testLoggerContextConfiguration", "exclude-only")
  }

  val testLegacyLoggerContextAttributes = register<Test>("testLegacyLoggerContextAttributes") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*LogbackLoggerContextSelectorTest")

    jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-logger-context-attributes=true")
    systemProperty("testLoggerContextConfiguration", "legacy")
  }

  val testLoggerContextAttributePrecedence = register<Test>("testLoggerContextAttributePrecedence") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*LogbackLoggerContextSelectorTest")

    jvmArgs(
      "-Dotel.instrumentation.logback-appender.experimental.logger-context-attributes.included=new",
      "-Dotel.instrumentation.logback-appender.experimental.capture-logger-context-attributes=true",
    )
    systemProperty("testLoggerContextConfiguration", "precedence")
  }

  val logstashMarkerTest = named<Test>("logstashMarkerTest") {
    jvmArgs("-Dotel.instrumentation.logback-appender.experimental.logstash-marker-attributes.included=key?")
  }

  val testLogstashMarkerAttributeExclusionsOnly = register<Test>("testLogstashMarkerAttributeExclusionsOnly") {
    testClassesDirs = sourceSets["logstashMarkerTest"].output.classesDirs
    classpath = sourceSets["logstashMarkerTest"].runtimeClasspath

    jvmArgs("-Dotel.instrumentation.logback-appender.experimental.logstash-marker-attributes.excluded=other")
    systemProperty("testLogstashMarkerConfiguration", "exclude-only")
  }

  val testLegacyLogstashMarkerAttributes = register<Test>("testLegacyLogstashMarkerAttributes") {
    testClassesDirs = sourceSets["logstashMarkerTest"].output.classesDirs
    classpath = sourceSets["logstashMarkerTest"].runtimeClasspath

    jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-logstash-marker-attributes=true")
    systemProperty("testLogstashMarkerConfiguration", "legacy")
  }

  val testLogstashMarkerAttributePrecedence = register<Test>("testLogstashMarkerAttributePrecedence") {
    testClassesDirs = sourceSets["logstashMarkerTest"].output.classesDirs
    classpath = sourceSets["logstashMarkerTest"].runtimeClasspath

    jvmArgs(
      "-Dotel.instrumentation.logback-appender.experimental.logstash-marker-attributes.included=key1",
      "-Dotel.instrumentation.logback-appender.experimental.capture-logstash-marker-attributes=true",
    )
    systemProperty("testLogstashMarkerConfiguration", "precedence")
  }

  check {
    dependsOn(
      testing.suites,
      testLegacyKeyValuePairAttributes,
      testMdcAttributeExclusionsOnly,
      testLegacyMdcAttributes,
      testLegacyMdcAttributesCaptureAll,
      testMdcAttributePrecedence,
      testLoggerContextAttributeExclusionsOnly,
      testLegacyLoggerContextAttributes,
      testLoggerContextAttributePrecedence,
      logstashMarkerTest,
      testLogstashMarkerAttributeExclusionsOnly,
      testLegacyLogstashMarkerAttributes,
      testLogstashMarkerAttributePrecedence,
    )
  }
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental-log-attributes=true")
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-code-attributes=true")
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-marker-attribute=true")
}
