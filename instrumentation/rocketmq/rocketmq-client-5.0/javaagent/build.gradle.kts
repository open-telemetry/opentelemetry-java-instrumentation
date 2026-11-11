import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.testcontainers-testing")
}

muzzle {
  pass {
    group.set("org.apache.rocketmq")
    module.set("rocketmq-client-java")
    versions.set("[5.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.rocketmq:rocketmq-client-java:5.0.0")

  testInstrumentation(project(":instrumentation:rocketmq:rocketmq-client-4.8:javaagent"))

  // earlier versions have bugs that may make tests flaky.
  testLibrary("org.apache.rocketmq:rocketmq-client-java:5.0.2")
}

testing.suites {
  register<JvmTestSuite>("rocketmq52Test") {
    dependencies {
      val rocketmqVersion = baseVersion("5.2.2").orLatest()
      implementation("org.apache.rocketmq:rocketmq-client-java:$rocketmqVersion")
      implementation("org.apache.rocketmq:rocketmq-tools:5.5.1")
      implementation("org.testcontainers:testcontainers")
    }
  }

  executions("test") {
    configureDefault {
      filter {
        excludeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
      }
      telemetryConfig(
        "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled",
        "true",
      )
    }

    register("Experimental") {
      filter {
        excludeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
      }
      systemProperty(
        "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled",
        "true",
      )
      systemProperty(
        "otel.instrumentation.rocketmq-client.experimental-span-attributes",
        "true",
      )
    }

    register("ReceiveSpanDisabled") {
      filter {
        includeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
      }
      include("**/RocketMqClientSuppressReceiveSpanTest.*")
    }

    // A SimpleConsumer pull has no process span, so it gets a receive span even when receive
    // telemetry is disabled by default.
    register("SimpleConsumerReceiveTelemetryDisabled") {
      filter {
        includeTestsMatching(
          "RocketMqSimpleConsumerTest.shouldInstrumentReceiveWhenReceiveTelemetryDisabled",
        )
      }
      include("**/RocketMqSimpleConsumerTest.*")
    }
  }

  executions("rocketmq52Test") {
    register("ReceiveTelemetry") {
      telemetryConfig(
        "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled",
        "true",
      )
    }

    register("Experimental") {
      telemetryConfig(
        "otel.instrumentation.rocketmq-client.experimental-span-attributes",
        "true",
      )
    }

    register("ExperimentalReceiveTelemetry") {
      telemetryConfig(
        "otel.instrumentation.rocketmq-client.experimental-span-attributes",
        "true",
      )
      telemetryConfig(
        "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled",
        "true",
      )
    }
  }
}

tasks.withType<Test>().configureEach {
  if (otelProps.denyUnsafe) {
    enabled = false
  }
}

tasks.check {
  dependsOn(testing.suites)
}
