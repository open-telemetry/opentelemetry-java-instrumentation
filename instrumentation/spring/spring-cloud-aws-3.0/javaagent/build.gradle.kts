import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("io.awspring.cloud")
    module.set("spring-cloud-aws-sqs")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.awspring.cloud:spring-cloud-aws-starter-sqs:3.0.0")
  implementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:library"))

  testInstrumentation(project(":instrumentation:aws-sdk:aws-sdk-2.2:javaagent"))

  testImplementation("org.elasticmq:elasticmq-rest-sqs_2.13")

  testLibrary("org.springframework.boot:spring-boot-starter-test:3.0.0")
  testLibrary("org.springframework.boot:spring-boot-starter-web:3.0.0")
}

testing.suites {
  executions("test") {
    // receive telemetry changes which operation counts consumed messages
    register("ReceiveTelemetry") {
      filter.includeTestsMatching("*AwsSqsTest.sqsBatchListener")
      telemetryConfig("otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled", "true")
    }
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

if (otelProps.denyUnsafe) {
  // org.elasticmq:elasticmq-rest-sqs_2.13 uses unsafe. Future versions are likely to fix this.
  tasks.withType<Test>().configureEach {
    enabled = false
  }
}
