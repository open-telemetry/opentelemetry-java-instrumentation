import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.testcontainers-testing")
}

muzzle {
  pass {
    group.set("org.apache.kafka")
    module.set("kafka-streams")
    versions.set("[0.11.0.0,)")
    assertInverse.set(true)
    excludeInstrumentationName("kafka-clients")
    excludeInstrumentationName("kafka-clients-metrics")
  }
}

dependencies {
  bootstrap(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))

  library("org.apache.kafka:kafka-streams:0.11.0.0")

  // Include kafka-clients instrumentation for tests.
  testInstrumentation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))

  testImplementation("org.testcontainers:testcontainers-kafka")
}

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
        implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
        implementation(project(":javaagent-bootstrap"))
        implementation(project(":javaagent-extension-api"))
        implementation("org.apache.kafka:kafka-streams:0.11.0.0")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  check {
    dependsOn("unitTests")
  }
}

testing.suites {
  executions("test") {
    configureDefault {
      filter {
        excludeTestsMatching("KafkaStreamsSuppressReceiveSpansTest")
      }
    }

    register("ReceiveSpansDisabled") {
      filter {
        includeTestsMatching("KafkaStreamsSuppressReceiveSpansTest")
      }
      include("**/KafkaStreamsSuppressReceiveSpansTest.*")
    }

    register("ReceiveTelemetry") {
      filter {
        excludeTestsMatching("KafkaStreamsSuppressReceiveSpansTest")
      }
      telemetryConfig(
        "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled",
        "true",
      )
    }

    register("Experimental") {
      filter {
        excludeTestsMatching("KafkaStreamsSuppressReceiveSpansTest")
      }
      telemetryConfig(
        "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled",
        "true",
      )
      telemetryConfig("otel.instrumentation.kafka.experimental-span-attributes", "true")
    }
  }
}

// kafka 4.1 requires java 11
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
