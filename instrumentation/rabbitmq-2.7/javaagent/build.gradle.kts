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
  // does not exist at the 2.7.0 muzzle floor. testCompileOnly lets the recovery test compile
  // against a client new enough to have it without pulling a newer client onto the test runtime
  // classpath, so the floor (2.7.0) still gets exercised by every other test; the recovery test
  // itself is gated with Assumptions.assumeTrue(testLatestDeps) and only actually runs when
  // testLatestDeps bumps the library() floor to a version that has the feature.
  testCompileOnly("com.rabbitmq:amqp-client:4.0.0")

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

  check {
    dependsOn(
      testExperimental,
      testMessagingPreview,
      testBothSemconv,
    )
  }
}
