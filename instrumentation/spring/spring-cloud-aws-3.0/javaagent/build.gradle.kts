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

if (otelProps.denyUnsafe) {
  // org.elasticmq:elasticmq-rest-sqs_2.13 uses unsafe. Future versions are likely to fix this.
  tasks.withType<Test>().configureEach {
    enabled = false
  }
}

tasks {
  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching(
        "io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0.AwsSqsTest.legacyBatchUsesReceiveParent",
      )
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.messaging.experimental.receive-telemetry.enabled=true",
    )
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching(
        "io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0.AwsSqsTest.stableBatchUsesAmbientParentWithReceiveContext",
      )
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.messaging.experimental.receive-telemetry.enabled=true,otel.semconv-stability.opt-in=messaging",
    )
  }

  check {
    dependsOn(testExperimental, testStableSemconv)
  }
}
