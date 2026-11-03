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

testing {
  suites {
    executions("test") {
      configureEach {
        if (otelProps.denyUnsafe) {
          enabled = false
        }
      }

      configureDefault {
        filter {
          excludeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
        }
        telemetryConfig("otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled", "true")
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
  }
}
