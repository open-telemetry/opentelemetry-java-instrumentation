import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.influxdb")
    module.set("influxdb-java")
    versions.set("[2.4,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("org.influxdb:influxdb-java:2.4")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testInstrumentation(project(":instrumentation:okhttp:okhttp-3.0:javaagent"))

  // we use methods that weren't present before 2.14 in tests
  testLibrary("org.influxdb:influxdb-java:2.14")
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          filter {
            excludeTestsMatching("InfluxDbQuerySanitizationDisabledTest")
          }
        }
        register("ExceptionSignalLogs") {
          jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
        }
      }
    }
    register<JvmTestSuite>("test24") {
      dependencies {
        implementation(project())
        implementation("org.influxdb:influxdb-java:2.4")
        implementation("org.testcontainers:testcontainers")
      }
      executions {
        register("ExceptionSignalLogs") {
          jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    // we disable the okhttp instrumentation, so we don't need to assert on the okhttp spans
    // from the okhttp instrumentation we need OkHttp3IgnoredTypesConfigurer to fix context leaks
    jvmArgs("-Dotel.instrumentation.okhttp.enabled=false")
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  if (!otelProps.testLatestDeps) {
    check {
      dependsOn(testing.suites)
    }
  }

  val testQuerySanitizationDisabled = register<Test>("testQuerySanitizationDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("InfluxDbQuerySanitizationDisabledTest")
    }
    systemProperty("metadataConfig", "otel.instrumentation.influxdb.query-sanitization.enabled=false")
    jvmArgs("-Dotel.instrumentation.influxdb.query-sanitization.enabled=false")
  }

  check {
    dependsOn(testQuerySanitizationDisabled)
  }
}
