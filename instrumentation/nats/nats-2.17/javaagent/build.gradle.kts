plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.nats")
    module.set("jnats")
    versions.set("[2.17.2,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.nats:jnats:2.17.2")

  implementation(project(":instrumentation:nats:nats-2.17:library"))
  testImplementation(project(":instrumentation:nats:nats-2.17:testing"))
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  check {
    dependsOn(testV3Preview)
  }
}
