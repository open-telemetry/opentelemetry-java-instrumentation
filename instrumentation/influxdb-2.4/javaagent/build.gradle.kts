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
    register<JvmTestSuite>("test24") {
      dependencies {
        implementation(project())
        implementation("org.influxdb:influxdb-java:2.4")
        implementation("org.testcontainers:testcontainers")
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
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  if (!otelProps.testLatestDeps) {
    check {
      dependsOn(testing.suites)
    }
  }

  test {
    filter {
      excludeTestsMatching("InfluxDbQuerySanitizationDisabledTest")
    }
  }

  val stableSemconvSuites = testing.suites.withType(JvmTestSuite::class)
    .associate { suite ->
      suite.name to register<Test>("${suite.name}StableSemconv") {
        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath

        filter {
          excludeTestsMatching("InfluxDbQuerySanitizationDisabledTest")
        }
        systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
        jvmArgs("-Dotel.semconv-stability.opt-in=database")
      }
    }

  val testQuerySanitizationDisabled = register<Test>("testQuerySanitizationDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("InfluxDbQuerySanitizationDisabledTest")
    }
    systemProperty("metadataConfig", "otel.instrumentation.common.db.query-sanitization.enabled=false")
    jvmArgs("-Dotel.instrumentation.common.db.query-sanitization.enabled=false")
  }

  val testQuerySanitizationEnabledOverride = register<Test>("testQuerySanitizationEnabledOverride") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("InfluxDbClientTest.testQueryWithTwoArguments")
    }
    systemProperty("metadataConfig", "otel.instrumentation.common.db.query-sanitization.enabled=false,otel.instrumentation.influxdb.query-sanitization.enabled=true")
    jvmArgs("-Dotel.instrumentation.common.db.query-sanitization.enabled=false")
    jvmArgs("-Dotel.instrumentation.influxdb.query-sanitization.enabled=true")
  }

  val testQuerySanitizationDisabledStableSemconv = register<Test>("testQuerySanitizationDisabledStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("InfluxDbQuerySanitizationDisabledTest")
    }
    systemProperty("metadataConfig", "otel.instrumentation.common.db.query-sanitization.enabled=false,otel.semconv-stability.opt-in=database")
    jvmArgs("-Dotel.instrumentation.common.db.query-sanitization.enabled=false")
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(
      if (otelProps.testLatestDeps) listOf(stableSemconvSuites.getValue("test")) else stableSemconvSuites.values,
      testQuerySanitizationDisabled,
      testQuerySanitizationDisabledStableSemconv,
      testQuerySanitizationEnabledOverride
    )
  }
}
