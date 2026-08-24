plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.projectreactor.kafka")
    module.set("reactor-kafka")
    versions.set("[1.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly(project(":muzzle"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  bootstrap(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))

  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  // using 1.3 to be able to implement several new KafkaReceiver methods added in 1.3.3 and 1.3.21
  // @NoMuzzle is used to ensure that this does not break muzzle checks
  compileOnly("io.projectreactor.kafka:reactor-kafka:1.3.21")

  testInstrumentation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.4:javaagent"))

  testImplementation(project(":instrumentation:reactor:reactor-kafka-1.0:testing"))

  testLibrary("io.projectreactor.kafka:reactor-kafka:1.0.0.RELEASE")
}

testing {
  suites {
    register<JvmTestSuite>("testV1_3_3") {
      dependencies {
        implementation(project(":instrumentation:reactor:reactor-kafka-1.0:testing"))

        implementation("io.projectreactor.kafka:reactor-kafka:${baseVersion("1.3.3").orLatest()}")
        if (otelProps.testLatestDeps) {
          implementation("io.projectreactor:reactor-core:3.4.+")
        }
      }

      targets {
        all {
          testTask.configure {
            systemProperty("hasConsumerGroup", true)
          }
        }
      }
    }

    register<JvmTestSuite>("testV1_3_21") {
      dependencies {
        implementation(project(":instrumentation:reactor:reactor-kafka-1.0:testing"))

        implementation("io.projectreactor.kafka:reactor-kafka:${baseVersion("1.3.21").orLatest()}")
        if (otelProps.testLatestDeps) {
          implementation("io.projectreactor:reactor-core:3.4.+")
        }
      }

      targets {
        all {
          testTask.configure {
            systemProperty("hasConsumerGroup", true)
          }
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
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

  val testReceiveSpansDisabled = register<Test>("testReceiveSpansDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    systemProperty("hasConsumerGroup", otelProps.testLatestDeps)
  }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("hasConsumerGroup", otelProps.testLatestDeps)
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("hasConsumerGroup", otelProps.testLatestDeps)
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  val testMessagingPreviewReceiveSpansDisabled = register<Test>("testMessagingPreviewReceiveSpansDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("hasConsumerGroup", otelProps.testLatestDeps)
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=false")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testV1_3_3ReceiveSpansDisabled = register<Test>("testV1_3_3ReceiveSpansDisabled") {
    testClassesDirs = sourceSets["testV1_3_3"].output.classesDirs
    classpath = sourceSets["testV1_3_3"].runtimeClasspath
    isEnabled = project.tasks.named("testV1_3_3").get().enabled
    systemProperty("hasConsumerGroup", true)
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=false")
  }

  val testV1_3_3MessagingPreview = register<Test>("testV1_3_3MessagingPreview") {
    testClassesDirs = sourceSets["testV1_3_3"].output.classesDirs
    classpath = sourceSets["testV1_3_3"].runtimeClasspath
    isEnabled = project.tasks.named("testV1_3_3").get().enabled
    systemProperty("hasConsumerGroup", true)
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testV1_3_3BothSemconv = register<Test>("testV1_3_3BothSemconv") {
    testClassesDirs = sourceSets["testV1_3_3"].output.classesDirs
    classpath = sourceSets["testV1_3_3"].runtimeClasspath
    isEnabled = project.tasks.named("testV1_3_3").get().enabled
    systemProperty("hasConsumerGroup", true)
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  val testV1_3_3MessagingPreviewReceiveSpansDisabled =
    register<Test>("testV1_3_3MessagingPreviewReceiveSpansDisabled") {
      testClassesDirs = sourceSets["testV1_3_3"].output.classesDirs
      classpath = sourceSets["testV1_3_3"].runtimeClasspath
      isEnabled = project.tasks.named("testV1_3_3").get().enabled
      systemProperty("hasConsumerGroup", true)
      jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=false")
      jvmArgs("-Dotel.semconv-stability.preview=messaging")
      systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
    }

  val testV1_3_21ReceiveSpansDisabled = register<Test>("testV1_3_21ReceiveSpansDisabled") {
    testClassesDirs = sourceSets["testV1_3_21"].output.classesDirs
    classpath = sourceSets["testV1_3_21"].runtimeClasspath
    isEnabled = project.tasks.named("testV1_3_21").get().enabled
    systemProperty("hasConsumerGroup", true)
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=false")
  }

  val testV1_3_21MessagingPreview = register<Test>("testV1_3_21MessagingPreview") {
    testClassesDirs = sourceSets["testV1_3_21"].output.classesDirs
    classpath = sourceSets["testV1_3_21"].runtimeClasspath
    isEnabled = project.tasks.named("testV1_3_21").get().enabled
    systemProperty("hasConsumerGroup", true)
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testV1_3_21BothSemconv = register<Test>("testV1_3_21BothSemconv") {
    testClassesDirs = sourceSets["testV1_3_21"].output.classesDirs
    classpath = sourceSets["testV1_3_21"].runtimeClasspath
    isEnabled = project.tasks.named("testV1_3_21").get().enabled
    systemProperty("hasConsumerGroup", true)
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  val testV1_3_21MessagingPreviewReceiveSpansDisabled =
    register<Test>("testV1_3_21MessagingPreviewReceiveSpansDisabled") {
      testClassesDirs = sourceSets["testV1_3_21"].output.classesDirs
      classpath = sourceSets["testV1_3_21"].runtimeClasspath
      isEnabled = project.tasks.named("testV1_3_21").get().enabled
      systemProperty("hasConsumerGroup", true)
      jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=false")
      jvmArgs("-Dotel.semconv-stability.preview=messaging")
      systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
    }

  test {
    systemProperty("hasConsumerGroup", otelProps.testLatestDeps)
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.messaging.experimental.receive-telemetry.enabled=true",
    )
  }

  check {
    dependsOn(
      testing.suites,
      experimentalSuites,
      testReceiveSpansDisabled,
      testMessagingPreview,
      testBothSemconv,
      testMessagingPreviewReceiveSpansDisabled,
      testV1_3_3ReceiveSpansDisabled,
      testV1_3_3MessagingPreview,
      testV1_3_3BothSemconv,
      testV1_3_3MessagingPreviewReceiveSpansDisabled,
      testV1_3_21ReceiveSpansDisabled,
      testV1_3_21MessagingPreview,
      testV1_3_21BothSemconv,
      testV1_3_21MessagingPreviewReceiveSpansDisabled,
    )
  }
}
