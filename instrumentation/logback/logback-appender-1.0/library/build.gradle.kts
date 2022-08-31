plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation-appender-api-internal"))
  implementation(project(":instrumentation-appender-sdk-internal"))

  // pin the version strictly to avoid overriding by dependencyManagement versions
  compileOnly("ch.qos.logback:logback-classic") {
    version {
      strictly("0.9.16")
    }
  }
  compileOnly("org.slf4j:slf4j-api") {
    version {
      strictly("1.5.8")
    }
  }

  if (findProperty("testLatestDeps") as Boolean) {
    testImplementation("ch.qos.logback:logback-classic:+")
  } else {
    // TODO these versions are actually used during test
    // currently our tests fail for logback-classic 0.9.16
    testImplementation("ch.qos.logback:logback-classic") {
      version {
        strictly("1.2.11")
      }
    }
    testImplementation("org.slf4j:slf4j-api") {
      version {
        strictly("1.7.36")
      }
    }
  }

  testImplementation("io.opentelemetry:opentelemetry-sdk-logs")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}
