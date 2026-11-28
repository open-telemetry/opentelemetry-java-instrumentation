import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.apache.rocketmq:rocketmq-client:4.8.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("org.apache.rocketmq:rocketmq-test:4.8.0")

  testImplementation(project(":instrumentation:rocketmq:rocketmq-client-4.8:testing"))
}

testing.suites {
  executions("test") {
    configureEach {
      systemProperty("testLatestDeps", otelProps.testLatestDeps)
      // required on jdk17
      jvmArgs("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED")
      jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
      // with default settings tests will fail when disk is 90% full
      jvmArgs("-Drocketmq.broker.diskSpaceWarningLevelRatio=1.0")
    }

    register("Experimental") {
      filter {
        includeTestsMatching(
          "io.opentelemetry.instrumentation.rocketmqclient.v4_8.RocketMqClientTest",
        )
      }
      systemProperty("test.captureExperimentalSpanAttributes", "true")
      systemProperty(
        "metadataConfig",
        "RocketMqTelemetryBuilder.setCaptureExperimentalSpanAttributes(true)",
      )
    }
  }
}
