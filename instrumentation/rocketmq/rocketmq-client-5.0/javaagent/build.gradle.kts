plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.rocketmq")
    module.set("rocketmq-client-java")
    versions.set("[5.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.rocketmq:rocketmq-client-java:5.0.0")

  testInstrumentation(project(":instrumentation:rocketmq:rocketmq-client-4.8:javaagent"))

  // earlier versions have bugs that may make tests flaky.
  testLibrary("org.apache.rocketmq:rocketmq-client-java:5.0.2")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testReceiveSpanDisabled = register<Test>("testReceiveSpanDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
    }
    include("**/RocketMqClientSuppressReceiveSpanTest.*")
  }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
      excludeTestsMatching("SimpleConsumerAckOperationTest")
    }

    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testAckDefault = register<Test>("testAckDefault") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("SimpleConsumerAckOperationTest")
    }
    include("**/SimpleConsumerAckOperationTest.*")
  }

  val testMessagingOptIn = register<Test>("testMessagingOptIn") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("SimpleConsumerAckOperationTest")
    }
    include("**/SimpleConsumerAckOperationTest.*")
    jvmArgs("-Dotel.semconv-stability.opt-in=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=messaging")
  }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("SimpleConsumerAckOperationTest")
    }
    include("**/SimpleConsumerAckOperationTest.*")
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  val testSimpleConsumerReceiveSpanDisabled =
    register<Test>("testSimpleConsumerReceiveSpanDisabled") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter {
        includeTestsMatching("RocketMqSimpleConsumerTest.shouldHonorDisabledReceiveTelemetry")
      }
      include("**/RocketMqSimpleConsumerTest.*")
      jvmArgs("-Dotel.semconv-stability.preview=messaging")
      systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
    }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("RocketMqClientTest.testSendAndConsumeNormalMessage")
      includeTestsMatching("RocketMqClientTest.testConsumeFailure")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  test {
    filter {
      excludeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
      excludeTestsMatching("SimpleConsumerAckOperationTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.messaging.experimental.receive-telemetry.enabled=true",
    )
  }

  listOf(
    "test",
    "testReceiveSpanDisabled",
    "testMessagingPreview",
    "testSimpleConsumerReceiveSpanDisabled",
    "testBothSemconv",
  ).forEach { taskName ->
    named<Test>(taskName) {
      usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    }
  }

  check {
    dependsOn(
      testAckDefault,
      testReceiveSpanDisabled,
      testMessagingPreview,
      testMessagingOptIn,
      testSimpleConsumerReceiveSpanDisabled,
      testBothSemconv,
      testV3Preview,
    )
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}

// The javaagent test convention removes main output from test classpaths. These helper-level tests
// exercise the async lifecycle directly without loading the container-backed client.
afterEvaluate {
  listOf("testAckDefault", "testMessagingOptIn", "testV3Preview").forEach { taskName ->
    tasks.named<Test>(taskName) {
      classpath += sourceSets.main.get().output
    }
  }
}
