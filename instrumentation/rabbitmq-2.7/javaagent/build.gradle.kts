plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.rabbitmq")
    module.set("amqp-client")
    versions.set("[2.7.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:rabbitmq-2.7:bootstrap"))

  library("com.rabbitmq:amqp-client:2.7.0")

  // automatic recovery (Recoverable, RecoveryListener, ConnectionFactory#setAutomaticRecoveryEnabled)
  // does not exist at the 2.7.0 muzzle floor; the recovery test needs a client new enough to have
  // it, so bump just the test classpath -- the muzzle floor above is unaffected
  testLibrary("com.rabbitmq:amqp-client:4.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("org.springframework.amqp:spring-rabbit:1.1.0.RELEASE") {
    exclude("com.rabbitmq", "amqp-client")
  }

  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))

  testLibrary("io.projectreactor.rabbitmq:reactor-rabbitmq:1.0.0.RELEASE")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
    systemProperty("testLatestDeps", otelProps.testLatestDeps)

    systemProperty("otel.instrumentation.messaging.experimental.receive-telemetry.enabled", "true")

    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.rabbitmq.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.rabbitmq.experimental-span-attributes=true")
  }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("otel.instrumentation.messaging.experimental.receive-telemetry.enabled", "false")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  val testCaptureConnectionAttributes = register<Test>("testCaptureConnectionAttributes") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs(
      "-Dotel.instrumentation.rabbitmq.experimental.capture-vhost-name=true",
      "-Dotel.instrumentation.rabbitmq.experimental.capture-cluster-name=true",
    )
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.rabbitmq.experimental.capture-vhost-name=true," +
        "otel.instrumentation.rabbitmq.experimental.capture-cluster-name=true",
    )
  }

  check {
    dependsOn(
      testExperimental,
      testMessagingPreview,
      testBothSemconv,
      testCaptureConnectionAttributes,
    )
  }
}
