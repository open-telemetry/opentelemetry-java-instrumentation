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

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0.AwsSqsTest")
    }
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0.AwsSqsTest")
    }
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
  }

  val testMessagingPreviewReceiveSpansDisabled =
    register<Test>("testMessagingPreviewReceiveSpansDisabled") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter {
        includeTestsMatching("io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0.AwsSqsTest")
      }
      jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-spans.enabled=false")
      jvmArgs("-Dotel.semconv-stability.preview=messaging")
    }

  val testMessagingPreviewReceiveSpansEnabled =
    register<Test>("testMessagingPreviewReceiveSpansEnabled") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter {
        includeTestsMatching("io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0.AwsSqsTest")
      }
      jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-spans.enabled=true")
      jvmArgs("-Dotel.semconv-stability.preview=messaging")
    }

  // legacy semconv (no preview) with receive spans explicitly enabled: pins that an empty internal
  // listener poll still creates a receive span, unchanged from main
  val testReceiveSpansEnabled = register<Test>("testReceiveSpansEnabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0.AwsSqsTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-spans.enabled=true")
  }

  check {
    dependsOn(
      testMessagingPreview,
      testBothSemconv,
      testMessagingPreviewReceiveSpansDisabled,
      testMessagingPreviewReceiveSpansEnabled,
      testReceiveSpansEnabled,
    )
  }
}

if (otelProps.denyUnsafe) {
  // org.elasticmq:elasticmq-rest-sqs_2.13 uses unsafe. Future versions are likely to fix this.
  tasks.withType<Test>().configureEach {
    enabled = false
  }
}
