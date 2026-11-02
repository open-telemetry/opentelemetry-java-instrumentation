import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.testcontainers-testing")
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

  testImplementation(project(":instrumentation:reactor:reactor-kafka-1.0:testing"))

  testLibrary("io.projectreactor.kafka:reactor-kafka:1.0.0.RELEASE")
}

testing.suites {
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
  }

  register<JvmTestSuite>("receiveTelemetryDisabledUnitTests") {
    sources {
      java {
        srcDir("src/unitTests/java")
      }
    }

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
  }

  register<JvmTestSuite>("testV1_3_3") {
    dependencies {
      implementation(project(":instrumentation:reactor:reactor-kafka-1.0:testing"))

      implementation("io.projectreactor.kafka:reactor-kafka:${baseVersion("1.3.3").orLatest()}")
      if (otelProps.testLatestDeps) {
        implementation("io.projectreactor:reactor-core:3.4.+")
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
  }

  executions("unitTests") {
    configureDefault {
      jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")
    }
  }

  executions("test") {
    configureEach {
      systemProperty("hasConsumerGroup", otelProps.testLatestDeps)
    }

    configureDefault {
      telemetryConfig("otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled", "true")
    }

    register("Experimental") {
      telemetryConfig("otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled", "true")
      telemetryConfig("otel.instrumentation.kafka.experimental-span-attributes", "true")
      enabled = tasks.named<Test>("test").get().enabled
    }

    register("ReceiveSpansDisabled") {
      telemetryConfig("otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled", "false")
      enabled = tasks.named<Test>("test").get().enabled
    }
  }

  executions("testV1_3_3", "testV1_3_21") {
    configureEach {
      systemProperty("hasConsumerGroup", true)
    }

    register("Experimental") {
      telemetryConfig("otel.instrumentation.kafka.experimental-span-attributes", "true")
      enabled = tasks.named<Test>(name.removeSuffix("Experimental")).get().enabled
    }

    register("ReceiveTelemetry") {
      telemetryConfig("otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled", "true")
      enabled = tasks.named<Test>(name.removeSuffix("ReceiveTelemetry")).get().enabled
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
