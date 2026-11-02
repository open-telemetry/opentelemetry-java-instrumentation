import io.opentelemetry.instrumentation.gradle.executions
import org.gradle.api.plugins.jvm.JvmTestSuite

plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
  library("org.apache.kafka:kafka-clients:2.6.0")

  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:testing"))
  testImplementation("com.fasterxml.jackson.core:jackson-databind:2.10.2")

  testImplementation("org.testcontainers:testcontainers-kafka")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")

  testCompileOnly("com.google.auto.value:auto-value-annotations")
  testAnnotationProcessor("com.google.auto.value:auto-value")
}

testing.suites.named<JvmTestSuite>("test") {
  executions {
    configureEach {
      usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
      systemProperty("testLatestDeps", otelProps.testLatestDeps)
    }

    configureDefault {
      filter {
        excludeTestsMatching("*.internal.*")
        excludeTestsMatching("*.ExceptionHandlingTest")
        excludeTestsMatching("*.WrapperPropagationDisabledTest")
        excludeTestsMatching("*.WrapperSuppressReceiveSpansTest")
        excludeTestsMatching("*.InterceptorsSuppressReceiveSpansTest")
      }
    }

    register("Regression") {
      filter {
        includeTestsMatching("*.internal.*")
        includeTestsMatching("*.ExceptionHandlingTest")
        includeTestsMatching("*.WrapperPropagationDisabledTest")
        includeTestsMatching("*.WrapperSuppressReceiveSpansTest")
        includeTestsMatching("*.InterceptorsSuppressReceiveSpansTest")
      }
    }

    register("ReceiveTelemetry") {
      filter {
        includeTestsMatching("io.opentelemetry.instrumentation.kafkaclients.v2_6.InterceptorsTest.testInterceptors")
        includeTestsMatching("io.opentelemetry.instrumentation.kafkaclients.v2_6.WrapperTest.testWrappers")
        includeTestsMatching("io.opentelemetry.instrumentation.kafkaclients.v2_6.WrapperTest.testConsumerError")
      }
      systemProperty("test.messagingReceiveTelemetryEnabled", "true")
      systemProperty(
        "metadataConfig",
        "KafkaTelemetryBuilder.setMessagingReceiveTelemetryEnabled(true)"
      )
    }

    register("Experimental") {
      filter {
        includeTestsMatching("io.opentelemetry.instrumentation.kafkaclients.v2_6.InterceptorsTest.testInterceptors")
        includeTestsMatching("io.opentelemetry.instrumentation.kafkaclients.v2_6.WrapperTest.testWrappers")
      }
      systemProperty("test.messagingReceiveTelemetryEnabled", "true")
      systemProperty("test.captureExperimentalSpanAttributes", "true")
      systemProperty(
        "metadataConfig",
        "KafkaTelemetryBuilder.setMessagingReceiveTelemetryEnabled(true)," +
          "KafkaTelemetryBuilder.setCaptureExperimentalSpanAttributes(true)"
      )
    }

    register("ExperimentalRegression") {
      filter {
        includeTestsMatching("io.opentelemetry.instrumentation.kafkaclients.v2_6.WrapperPropagationDisabledTest.testWrappers")
        includeTestsMatching("io.opentelemetry.instrumentation.kafkaclients.v2_6.WrapperSuppressReceiveSpansTest.testWrappers")
      }
      systemProperty("test.captureExperimentalSpanAttributes", "true")
    }

    register("ExceptionSignalLogs") {
      filter {
        includeTestsMatching("io.opentelemetry.instrumentation.kafkaclients.v2_6.WrapperTest.testConsumerError")
      }
      systemProperty("test.messagingReceiveTelemetryEnabled", "true")
      jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
      systemProperty(
        "metadataConfig",
        "KafkaTelemetryBuilder.setMessagingReceiveTelemetryEnabled(true)," +
          "otel.semconv.exception.signal.preview=logs"
      )
    }
  }
}

// kafka 4.1 requires java 11
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
