plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.pulsar")
    module.set("pulsar-client")
    versions.set("[2.8.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.pulsar:pulsar-client:2.8.0")

  testImplementation("javax.annotation:javax.annotation-api:1.3.2")
  testImplementation("org.testcontainers:testcontainers-pulsar")
  testLibrary("org.apache.pulsar:pulsar-client-admin:2.8.0")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("io.opentelemetry.pulsar-2.8.debug", "true")
  }

  val testReceiveSpanDisabled = register<Test>("testReceiveSpanDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("PulsarClientSuppressReceiveSpansTest")
    }
    include("**/PulsarClientSuppressReceiveSpansTest.*")
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      excludeTestsMatching("PulsarClientSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")

    jvmArgs("-Dotel.instrumentation.pulsar.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.pulsar.experimental-span-attributes=true")
  }

  // The task name follows the existing pattern; it does not indicate that the messaging
  // conventions are stable.
  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("PulsarClientTest.testConsumeNonPartitionedTopic")
      includeTestsMatching("PulsarClientTest.testConsumeNonPartitionedTopicUsingReceive")
    }
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=messaging")
  }

  test {
    filter {
      excludeTestsMatching("PulsarClientSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.messaging.experimental.receive-telemetry.enabled=true",
    )
  }

  check {
    dependsOn(testReceiveSpanDisabled, testExperimental, testStableSemconv)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
