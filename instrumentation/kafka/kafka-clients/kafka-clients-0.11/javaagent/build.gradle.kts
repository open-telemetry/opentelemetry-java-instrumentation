import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig
import io.opentelemetry.instrumentation.gradle.unitTests

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
  unitTests {
    dependencies {
      implementation(project(":instrumentation-api-incubator"))
      implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
      implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
      implementation(project(":instrumentation:kafka:kafka-streams-0.11:javaagent"))
      implementation(project(":javaagent-bootstrap"))
      implementation(project(":javaagent-extension-api"))
      implementation("io.opentelemetry:opentelemetry-api")
      implementation("org.apache.kafka:kafka-clients:0.11.0.0")
    }
  }

  executions("test") {
    configureEach {
      systemProperty("testLatestDeps", otelProps.testLatestDeps)
    }

    configureDefault {
      filter {
        excludeTestsMatching("KafkaClientPropagationDisabledTest")
        excludeTestsMatching("KafkaClientSuppressReceiveSpansTest")
        excludeTestsMatching("OpenTelemetryMetricsReporterTest")
      }
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

    register("ReceiveTelemetry") {
      filter {
        excludeTestsMatching("KafkaClientPropagationDisabledTest")
        excludeTestsMatching("KafkaClientSuppressReceiveSpansTest")
        excludeTestsMatching("OpenTelemetryMetricsReporterTest")
      }
      telemetryConfig(
        "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled",
        "true",
      )
    }

    register("Experimental") {
      filter {
        excludeTestsMatching("KafkaClientPropagationDisabledTest")
        excludeTestsMatching("KafkaClientSuppressReceiveSpansTest")
        excludeTestsMatching("OpenTelemetryMetricsReporterTest")
      }
      telemetryConfig(
        "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled",
        "true",
      )
      telemetryConfig("otel.instrumentation.kafka.experimental-span-attributes", "true")
    }

    register("Metrics") {
      filter {
        includeTestsMatching("*DefaultEnablementTest")
        includeTestsMatching("OpenTelemetryMetricsReporterTest")
      }
      telemetryConfig("otel.instrumentation.kafka-clients-metrics.enabled", "true")
    }
  }
}

// kafka 4.1 requires java 11
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
