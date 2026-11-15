import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.rocketmq")
    module.set("rocketmq-client")
    versions.set("[4.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.rocketmq:rocketmq-client:4.8.0")

  implementation(project(":instrumentation:rocketmq:rocketmq-client-4.8:library"))

  testInstrumentation(project(":instrumentation:rocketmq:rocketmq-client-5.0:javaagent"))
  testImplementation(project(":instrumentation:rocketmq:rocketmq-client-4.8:testing"))

  testLibrary("org.apache.rocketmq:rocketmq-test:4.8.0")
}

testing.suites {
  executions("test") {
    configureEach {
      systemProperty("testLatestDeps", otelProps.testLatestDeps)

      // required on jdk17
      jvmArgs("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED")
      jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

      jvmArgs("-Dotel.instrumentation.common.controller-telemetry.enabled=true")

      // with default settings tests will fail when disk is 90% full
      jvmArgs("-Drocketmq.broker.diskSpaceWarningLevelRatio=1.0")
    }

    register("Experimental") {
      telemetryConfig("otel.instrumentation.rocketmq-client.experimental-span-attributes", "true")
    }

    register("CreateSpansDisabled") {
      systemProperty("testBatchCreateSpansDisabled", "true")
      telemetryConfig(
        "otel.instrumentation.rocketmq-client.message-create-spans.enabled",
        "false",
      )
    }

    register("BatchSendSuppression") {
      filter {
        includeTestsMatching(
          "io.opentelemetry.instrumentation.rocketmqclient.v4_8.RocketMqClientTest.testBatchSendSuppression",
        )
      }
      systemProperty("testBatchSendSuppression", "true")
      telemetryConfig("otel.instrumentation.common.span-suppression-strategy", "span-kind")
    }
  }
}
