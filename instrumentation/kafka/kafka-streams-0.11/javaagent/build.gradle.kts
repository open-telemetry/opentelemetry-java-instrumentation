plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.kafka")
    module.set("kafka-streams")
    versions.set("[0.11.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))

  library("org.apache.kafka:kafka-streams:0.11.0.0")

  // Include kafka-clients instrumentation for tests.
  testInstrumentation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))

  testImplementation("com.google.guava:guava")
  testImplementation("org.testcontainers:testcontainers-kafka")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testReceiveSpansDisabled = register<Test>("testReceiveSpansDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("KafkaStreamsSuppressReceiveSpansTest")
    }
    include("**/KafkaStreamsSuppressReceiveSpansTest.*")
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      excludeTestsMatching("KafkaStreamsSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")

    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.kafka.experimental-span-attributes=true")
  }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("KafkaStreamsSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  test {
    filter {
      excludeTestsMatching("KafkaStreamsSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(testReceiveSpansDisabled)
    dependsOn(testExperimental)
    dependsOn(testV3Preview)
  }
}

// kafka 4.1 requires java 11
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
