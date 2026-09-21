plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.projectreactor.kafka")
    module.set("reactor-kafka")
    versions.set("[1.0.0,)")
    assertInverse.set(true)
    excludeInstrumentationName("kafka-clients")
    excludeInstrumentationName("kafka-clients-metrics")
  }
}

dependencies {
  compileOnly(project(":muzzle"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  bootstrap(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))

  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))
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
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
        implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
        implementation(project(":instrumentation:reactor:reactor-3.1:library"))
        implementation(project(":javaagent-bootstrap"))
        implementation(project(":javaagent-extension-api"))
        implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
        implementation("io.projectreactor.kafka:reactor-kafka:1.0.0.RELEASE")
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
            jvmArgs("-Dotel.semconv-stability.preview=messaging")
          }
        }
      }
    }

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
    if (name != "unitTests") {
      usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
      systemProperty("collectMetadata", otelProps.collectMetadata)
    }
  }

  val agentTestSuites = testing.suites.withType(JvmTestSuite::class)
    .matching { it.name != "unitTests" }

  val experimentalSuites = agentTestSuites
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

  val messagingPreviewSuites = agentTestSuites
    .map { suite ->
      register<Test>("${suite.name}MessagingPreview") {
        val sourceTask = named<Test>(suite.name).get()
        setJvmArgs(sourceTask.jvmArgs)
        setSystemProperties(sourceTask.systemProperties)

        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath

        val semconvConfig = "otel.semconv-stability.preview=messaging"
        jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")
        jvmArgs("-D$semconvConfig")
        systemProperty(
          "metadataConfig",
          listOfNotNull(sourceTask.systemProperties["metadataConfig"], semconvConfig).joinToString(","),
        )
        isEnabled = sourceTask.enabled
      }
    }

  val bothSemconvSuites = agentTestSuites
    .map { suite ->
      register<Test>("${suite.name}BothSemconv") {
        val sourceTask = named<Test>(suite.name).get()
        setJvmArgs(sourceTask.jvmArgs)
        setSystemProperties(sourceTask.systemProperties)

        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath

        val semconvConfig = "otel.semconv-stability.preview=messaging/dup"
        jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")
        jvmArgs("-D$semconvConfig")
        systemProperty(
          "metadataConfig",
          listOfNotNull(sourceTask.systemProperties["metadataConfig"], semconvConfig).joinToString(","),
        )
        isEnabled = sourceTask.enabled
      }
    }

  val messagingPreviewReceiveSpansDisabledSuites =
    agentTestSuites
      .map { suite ->
        register<Test>("${suite.name}MessagingPreviewReceiveSpansDisabled") {
          val sourceTask = named<Test>(suite.name).get()
          setJvmArgs(sourceTask.jvmArgs)
          setSystemProperties(sourceTask.systemProperties)

          testClassesDirs = suite.sources.output.classesDirs
          classpath = suite.sources.runtimeClasspath

          val semconvConfig = "otel.semconv-stability.preview=messaging"
          jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=false")
          jvmArgs("-D$semconvConfig")
          systemProperty("metadataConfig", semconvConfig)
          isEnabled = sourceTask.enabled
        }
      }

  test {
    systemProperty("hasConsumerGroup", otelProps.testLatestDeps)
    jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true",
    )
  }

  check {
    dependsOn(
      testing.suites,
      experimentalSuites,
      testReceiveSpansDisabled,
      messagingPreviewSuites,
      bothSemconvSuites,
      messagingPreviewReceiveSpansDisabledSuites,
    )
  }
}
