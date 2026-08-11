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
