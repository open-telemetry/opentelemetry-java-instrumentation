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
  testImplementation("org.apache.pulsar:pulsar-client-admin:2.8.0")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testReceiveSpanDisabled by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("PulsarClientSuppressReceiveSpansTest")
    }
    include("**/PulsarClientSuppressReceiveSpansTest.*")
  }

  val testExperimental by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      excludeTestsMatching("PulsarClientSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")

    jvmArgs("-Dotel.instrumentation.pulsar.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.pulsar.experimental-span-attributes=true")
  }

  val testExceptionSignalLogs by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("PulsarClientSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.semconv.exception.signal.opt-in=logs")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    systemProperty("metadataConfig", "otel.semconv.exception.signal.opt-in=logs")
  }

  test {
    filter {
      excludeTestsMatching("PulsarClientSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(testReceiveSpanDisabled, testExperimental, testExceptionSignalLogs)
  }

  if (findProperty("denyUnsafe") as Boolean) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
