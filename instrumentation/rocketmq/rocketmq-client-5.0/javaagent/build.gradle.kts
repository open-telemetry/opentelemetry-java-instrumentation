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
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testReceiveSpanDisabled = register<Test>("testReceiveSpanDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
    }
    include("**/RocketMqClientSuppressReceiveSpanTest.*")
  }

  // stable messaging semconv with receive spans off (the default): no receive span is created.
  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
    }
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  // stable messaging semconv with receive spans opted in: the receive-span assertions.
  val testMessagingPreviewReceiveSpansEnabled =
    register<Test>("testMessagingPreviewReceiveSpansEnabled") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter {
        excludeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
      }
      jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-spans.enabled=true")
      jvmArgs("-Dotel.semconv-stability.preview=messaging")
      systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
    }

  // v3-preview with receive spans off (the default): no receive span is created.
  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
    }
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  // v3-preview with receive spans opted in: the receive-span assertions.
  val testV3PreviewReceiveSpansEnabled = register<Test>("testV3PreviewReceiveSpansEnabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-spans.enabled=true")
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("RocketMqClientTest.testSendAndConsumeNormalMessage")
      includeTestsMatching("RocketMqClientTest.testConsumeFailure")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-spans.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  test {
    filter {
      excludeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-spans.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.messaging.experimental.receive-spans.enabled=true",
    )
  }

  check {
    dependsOn(
      testReceiveSpanDisabled,
      testMessagingPreview,
      testMessagingPreviewReceiveSpansEnabled,
      testV3Preview,
      testV3PreviewReceiveSpansEnabled,
      testBothSemconv,
    )
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
