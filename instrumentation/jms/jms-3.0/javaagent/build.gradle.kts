plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("jakarta.jms")
    module.set("jakarta.jms-api")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
  fail {
    group.set("javax.jms")
    module.set("jms-api")
    versions.set("(,)")
  }
  fail {
    group.set("javax.jms")
    module.set("javax.jms-api")
    versions.set("(,)")
  }
}

dependencies {
  bootstrap(project(":instrumentation:jms:jms-common-1.1:bootstrap"))
  implementation(project(":instrumentation:jms:jms-common-1.1:javaagent"))

  library("jakarta.jms:jakarta.jms-api:3.0.0")

  testImplementation("org.apache.activemq:artemis-jakarta-client:2.27.1")

  testInstrumentation(project(":instrumentation:jms:jms-1.1:javaagent"))
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testReceiveSpansDisabled = register<Test>("testReceiveSpansDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("Jms3SuppressReceiveSpansTest")
    }
    include("**/Jms3SuppressReceiveSpansTest.*")
  }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("Jms3SuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testMessagingPreviewReceiveSpansDisabled =
    register<Test>("testMessagingPreviewReceiveSpansDisabled") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath

      filter {
        includeTestsMatching("Jms3SuppressReceiveSpansTest")
      }
      include("**/Jms3SuppressReceiveSpansTest.*")
      jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=false")
      jvmArgs("-Dotel.semconv-stability.preview=messaging")
      systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
    }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("Jms3SuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  val testV3PreviewReceiveSpansDisabled =
    register<Test>("testV3PreviewReceiveSpansDisabled") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath

      filter {
        includeTestsMatching("Jms3SuppressReceiveSpansTest")
      }
      include("**/Jms3SuppressReceiveSpansTest.*")
      jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=false")
      jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
      systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
    }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("Jms3SuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  test {
    filter {
      excludeTestsMatching("Jms3SuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.messaging.experimental.receive-telemetry.enabled=true",
    )
  }

  check {
    dependsOn(
      testing.suites,
      testReceiveSpansDisabled,
      testMessagingPreview,
      testMessagingPreviewReceiveSpansDisabled,
      testV3Preview,
      testV3PreviewReceiveSpansDisabled,
      testBothSemconv,
    )
  }
}
