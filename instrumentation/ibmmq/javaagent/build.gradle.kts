plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.ibm.mq")
    module.set("com.ibm.mq.allclient")
    versions.set("[9.0.4.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.ibm.mq:com.ibm.mq.allclient:9.3.5.0")

  // The allclient artifact does not bundle the JMS API; needed for the
  // javax.jms.MessageListener class literal used by the async listener path.
  compileOnly("javax.jms:jms-api:1.1-rev-1")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
    systemProperty("testLatestDeps", otelProps.testLatestDeps)

    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")

    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
