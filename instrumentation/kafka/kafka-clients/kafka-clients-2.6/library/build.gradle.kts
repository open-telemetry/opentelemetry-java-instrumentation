plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
  library("org.apache.kafka:kafka-clients:2.6.0")

  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:testing"))
  testImplementation("com.fasterxml.jackson.core:jackson-databind:2.10.2")

  testImplementation("org.testcontainers:testcontainers-kafka")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")

  testCompileOnly("com.google.auto.value:auto-value-annotations")
  testAnnotationProcessor("com.google.auto.value:auto-value")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  val testDefaultTelemetry = register<Test>("testDefaultTelemetry") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("InterceptorsDefaultTest")
    }
  }

  val testInterceptors = register<Test>("testInterceptors") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("InterceptorsTest")
    }
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true," +
        "otel.instrumentation.common.messaging.headers.excluded=*-Excluded-Header," +
        "otel.instrumentation.common.messaging.headers.included=Test-Message-*"
    )
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("InterceptorsWithExperimentalAttributesTest")
    }
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true," +
        "otel.instrumentation.common.messaging.headers.excluded=*-Excluded-Header," +
        "otel.instrumentation.common.messaging.headers.included=Test-Message-*," +
        "otel.instrumentation.kafka.experimental-span-attributes=true"
    )
  }

  val testExceptionSignalLogs = register<Test>("testExceptionSignalLogs") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("io.opentelemetry.instrumentation.kafkaclients.v2_6.WrapperTest.testConsumerError")
    }
    jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true," +
        "otel.semconv.exception.signal.preview=logs"
    )
  }

  check {
    dependsOn(
      testDefaultTelemetry,
      testInterceptors,
      testExperimental,
      testExceptionSignalLogs,
    )
  }
}

// kafka 4.1 requires java 11
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
