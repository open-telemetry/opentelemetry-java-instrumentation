plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.rabbitmq")
    module.set("amqp-client")
    versions.set("[2.7.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.rabbitmq:amqp-client:2.7.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("org.springframework.amqp:spring-rabbit:1.1.0.RELEASE") {
    exclude("com.rabbitmq", "amqp-client")
  }

  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))

  testLibrary("io.projectreactor.rabbitmq:reactor-rabbitmq:1.0.0.RELEASE")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
    systemProperty("testLatestDeps", otelProps.testLatestDeps)

    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      excludeTestsMatching("RabbitMqTest.testEmptyPullReceiveReceiveSpansOff")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-spans.enabled=true")
    jvmArgs("-Dotel.instrumentation.rabbitmq.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.rabbitmq.experimental-span-attributes=true")
  }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("RabbitMqTest.testEmptyPullReceiveReceiveSpansOff")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-spans.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("RabbitMqTest.testEmptyPullReceiveReceiveSpansOff")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-spans.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  // v3-preview with receive spans off (the default): an application-initiated empty pull records
  // metrics but produces no receive span.
  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("RabbitMqTest.testEmptyPullReceiveReceiveSpansOff")
    }
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  // v3-preview with receive spans opted in: the receive-span assertions.
  val testV3PreviewReceiveSpansEnabled = register<Test>("testV3PreviewReceiveSpansEnabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("RabbitMqTest.testEmptyPullReceiveReceiveSpansOff")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-spans.enabled=true")
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  test {
    filter {
      excludeTestsMatching("RabbitMqTest.testEmptyPullReceiveReceiveSpansOff")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-spans.enabled=true")
  }

  check {
    dependsOn(
      testExperimental,
      testMessagingPreview,
      testV3Preview,
      testV3PreviewReceiveSpansEnabled,
      testBothSemconv,
    )
  }
}
