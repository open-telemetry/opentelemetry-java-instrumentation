plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.ibm.mq")
    module.set("com.ibm.mq.allclient")
    versions.set("[9.0.4.0,)")
    // No assertInverse: 9.0.4.0 is com.ibm.mq.allclient's first-ever published version (confirmed
    // against Maven Central), so there is no earlier real version for the generated "should fail
    // below the floor" check to run against -- it resolves to an empty classpath, and this
    // module's classLoaderOptimization() correctly declines to engage with an empty classpath,
    // which muzzle then reports as an unexpected pass rather than recognizing there was nothing to
    // test.
  }
}

dependencies {
  implementation(project(":instrumentation:ibmmq:ibmmq-common:javaagent"))

  library("com.ibm.mq:com.ibm.mq.allclient:9.3.5.0")

  // The allclient artifact does not bundle the JMS API; needed for the
  // javax.jms.MessageListener/Message class literals used by the async listener and receive paths.
  compileOnly("javax.jms:jms-api:1.1-rev-1")

  // compileOnly above does not reach the test classpath. JMS 2.0 is used for tests because
  // Connection/Session only became AutoCloseable in 2.0 (try-with-resources will not compile against
  // jms-api 1.1). Main source still targets 1.1.
  testImplementation("javax.jms:javax.jms-api:2.0.1")

  // This module only enriches spans that the generic JMS instrumentation creates, so that module
  // must be present in the test agent for there to be anything to enrich.
  testInstrumentation(project(":instrumentation:jms:jms-1.1:javaagent"))
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
