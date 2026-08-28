plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.ibm.mq")
    module.set("com.ibm.mq.jakarta.client")
    versions.set("[9.3.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:ibmmq:ibmmq-common:javaagent"))

  library("com.ibm.mq:com.ibm.mq.jakarta.client:9.3.5.0")

  // com.ibm.mq.jakarta.client does not bundle the JMS API; needed for the
  // jakarta.jms.MessageListener/Message class literals used by the async listener and receive
  // paths.
  compileOnly("jakarta.jms:jakarta.jms-api:3.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("jakarta.jms:jakarta.jms-api:3.0.0")

  // This module only enriches spans that the generic JMS instrumentation creates, so that module
  // must be present in the test agent for there to be anything to enrich.
  testInstrumentation(project(":instrumentation:jms:jms-3.0:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
    systemProperty("testLatestDeps", otelProps.testLatestDeps)

    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")

    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  // The queue manager identifier is an opt_in attribute, so the default test task asserts it is
  // absent and this task asserts it is emitted once enabled.
  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.ibmmq.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.ibmmq.experimental-span-attributes=true")
  }

  check {
    dependsOn(testExperimental)
  }
}
