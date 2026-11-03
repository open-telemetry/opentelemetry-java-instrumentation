import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.testcontainers-testing")
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

testing.suites {
  executions("test") {
    configureEach {
      systemProperty("testLatestDeps", otelProps.testLatestDeps)
    }

    configureDefault {
      filter {
        excludeTestsMatching("KafkaClientPropagationDisabledTest")
        excludeTestsMatching("KafkaClientSuppressReceiveSpansTest")
      }
      jvmArgs("-Dotel.instrumentation.kafka-clients-metrics.enabled=true")
    }

    register("PropagationDisabled") {
      filter {
        includeTestsMatching("KafkaClientPropagationDisabledTest")
      }
      include("**/KafkaClientPropagationDisabledTest.*")
      jvmArgs("-Dotel.instrumentation.kafka.producer-propagation.enabled=false")
    }

    register("ReceiveSpansDisabled") {
      filter {
        includeTestsMatching("KafkaClientSuppressReceiveSpansTest")
      }
      include("**/KafkaClientSuppressReceiveSpansTest.*")
    }

    register("Disabled") {
      filter {
        includeTestsMatching("*DefaultEnablementTest")
      }
    }

    register("ReceiveTelemetry") {
      filter {
        excludeTestsMatching("KafkaClientPropagationDisabledTest")
        excludeTestsMatching("KafkaClientSuppressReceiveSpansTest")
      }
      telemetryConfig(
        "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled",
        "true",
      )
      jvmArgs("-Dotel.instrumentation.kafka-clients-metrics.enabled=true")
    }

    register("Experimental") {
      filter {
        excludeTestsMatching("KafkaClientPropagationDisabledTest")
        excludeTestsMatching("KafkaClientSuppressReceiveSpansTest")
      }
      telemetryConfig(
        "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled",
        "true",
      )
      telemetryConfig("otel.instrumentation.kafka.experimental-span-attributes", "true")
      jvmArgs("-Dotel.instrumentation.kafka-clients-metrics.enabled=true")
    }
  }
}

// kafka 4.1 requires java 11
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
