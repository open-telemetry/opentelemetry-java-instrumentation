plugins {
  id("otel.javaagent-testing")
}

dependencies {
  library("io.vertx:vertx-kafka-client:3.6.0")
  // vertx-codegen is needed for Xlint's annotation checking
  library("io.vertx:vertx-codegen:3.6.0")

  testImplementation(project(":instrumentation:vertx:vertx-kafka-client-3.6:testing"))

  testInstrumentation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-kafka-client-3.6:javaagent"))

  latestDepTestLibrary("io.vertx:vertx-kafka-client:3.+") // see vertx-kafka-client-4-testing module
  latestDepTestLibrary("io.vertx:vertx-codegen:3.+") // see vertx-kafka-client-4-testing module
}

testing {
  suites {
    register<JvmTestSuite>("testNoReceiveTelemetry") {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-kafka-client-3.6:testing"))

        val version = baseVersion("3.6.0").orLatest("3.+")
        implementation("io.vertx:vertx-kafka-client:$version")
        implementation("io.vertx:vertx-codegen:$version")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  test {
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.messaging.experimental.receive-telemetry.enabled=true",
    )
  }

  val experimentalSuites = testing.suites.withType(JvmTestSuite::class)
    .map { suite ->
      register<Test>("${suite.name}Experimental") {
        val sourceTask = named<Test>(suite.name).get()
        setJvmArgs(sourceTask.jvmArgs)
        setSystemProperties(sourceTask.systemProperties)

        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath

        val experimentalConfig = "otel.instrumentation.kafka.experimental-span-attributes=true"
        jvmArgs("-D$experimentalConfig")
        systemProperty(
          "metadataConfig",
          listOfNotNull(sourceTask.systemProperties["metadataConfig"], experimentalConfig).joinToString(","),
        )
        isEnabled = sourceTask.enabled
      }
    }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  val testMessagingPreviewNoReceiveTelemetry = register<Test>("testMessagingPreviewNoReceiveTelemetry") {
    testClassesDirs = sourceSets["testNoReceiveTelemetry"].output.classesDirs
    classpath = sourceSets["testNoReceiveTelemetry"].runtimeClasspath
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=false")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  check {
    dependsOn(testing.suites, experimentalSuites, testMessagingPreview, testBothSemconv, testMessagingPreviewNoReceiveTelemetry)
  }
}
