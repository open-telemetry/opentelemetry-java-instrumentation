plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.rocketmq")
    module.set("rocketmq-client")
    versions.set("[4.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.rocketmq:rocketmq-client:4.8.0")

  implementation(project(":instrumentation:rocketmq:rocketmq-client-4.8:library"))

  testInstrumentation(project(":instrumentation:rocketmq:rocketmq-client-5.0:javaagent"))
  testImplementation(project(":instrumentation:rocketmq:rocketmq-client-4.8:testing"))

  testLibrary("org.apache.rocketmq:rocketmq-test:4.8.0")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)

    systemProperty("testLatestDeps", otelProps.testLatestDeps)

    // required on jdk17
    jvmArgs("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")

    // with default settings tests will fail when disk is 90% full
    jvmArgs("-Drocketmq.broker.diskSpaceWarningLevelRatio=1.0")
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.rocketmq-client.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.rocketmq-client.experimental-span-attributes=true")
  }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testMessagingPreviewCreateSpansDisabled =
    register<Test>("testMessagingPreviewCreateSpansDisabled") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      jvmArgs("-Dotel.semconv-stability.preview=messaging")
      jvmArgs("-Dotel.instrumentation.rocketmq-client.message-create-spans.enabled=false")
      systemProperty("testBatchCreateSpansDisabled", "true")
      systemProperty(
        "metadataConfig",
        "otel.semconv-stability.preview=messaging," +
          "otel.instrumentation.rocketmq-client.message-create-spans.enabled=false",
      )
    }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  val testBatchSendSuppression = register<Test>("testBatchSendSuppression") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching(
        "io.opentelemetry.instrumentation.rocketmqclient.v4_8.RocketMqClientTest.testBatchSendSuppression",
      )
    }
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    jvmArgs("-Dotel.instrumentation.experimental.span-suppression-strategy=span-kind")
    systemProperty("testBatchSendSuppression", "true")
    systemProperty(
      "metadataConfig",
      "otel.semconv-stability.preview=messaging," +
        "otel.instrumentation.experimental.span-suppression-strategy=span-kind",
    )
  }

  check {
    dependsOn(
      testExperimental,
      testMessagingPreview,
      testMessagingPreviewCreateSpansDisabled,
      testBatchSendSuppression,
      testBothSemconv,
    )
  }
}
