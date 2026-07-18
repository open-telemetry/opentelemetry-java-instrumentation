plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.kafka")
    module.set("kafka-clients")
    versions.set("[0.11.0.0,)")
    assertInverse.set(true)
    excludeInstrumentationName("kafka-clients-metrics")
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  bootstrap(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))

  library("org.apache.kafka:kafka-clients:0.11.0.0")

  testImplementation("org.testcontainers:testcontainers-kafka")
  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:testing"))
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testPropagationDisabled = register<Test>("testPropagationDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("KafkaClientPropagationDisabledTest")
    }
    include("**/KafkaClientPropagationDisabledTest.*")
    jvmArgs("-Dotel.instrumentation.kafka.producer-propagation.enabled=false")
    systemProperty("metadataConfig", "otel.instrumentation.kafka.producer-propagation.enabled=false")
  }

  val testReceiveSpansDisabled = register<Test>("testReceiveSpansDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("KafkaClientSuppressReceiveSpansTest")
    }
    include("**/KafkaClientSuppressReceiveSpansTest.*")
  }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("KafkaClientDefaultTest.testKafkaProducerAndConsumerSpan")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=messaging")
  }

  val testMessagingPreviewDup = register<Test>("testMessagingPreviewDup") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("KafkaClientDefaultTest.testReceiveDoesNotParentProcessSpan")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=messaging/dup")
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      excludeTestsMatching("KafkaClientPropagationDisabledTest")
      excludeTestsMatching("KafkaClientSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")

    systemProperty("metadataConfig", "otel.instrumentation.kafka.experimental-span-attributes=true")
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("KafkaClientPropagationDisabledTest")
      excludeTestsMatching("KafkaClientSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    // kafka metrics are disabled by default with v3-preview enabled
    jvmArgs("-Dotel.instrumentation.kafka-clients-metrics.enabled=true")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=messaging")
  }

  check {
    dependsOn(testStableSemconv)
  }

  test {
    filter {
      excludeTestsMatching("KafkaClientPropagationDisabledTest")
      excludeTestsMatching("KafkaClientSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(
      testPropagationDisabled,
      testReceiveSpansDisabled,
      testMessagingPreview,
      testMessagingPreviewDup,
      testExperimental,
    )
  }
}

// kafka 4.1 requires java 11
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
