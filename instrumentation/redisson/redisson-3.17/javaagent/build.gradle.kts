plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.redisson")
    module.set("redisson")
    versions.set("[3.17.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.redisson:redisson:3.17.0")

  implementation(project(":instrumentation:redisson:redisson-common-3.0:javaagent"))

  testInstrumentation(project(":instrumentation:redisson:redisson-3.0:javaagent"))

  testImplementation(project(":instrumentation:redisson:redisson-common-3.0:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*Redisson*ClientTest.atomicBatch*")
    jvmArgs("-Dotel.semconv-stability.opt-in=database/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database/dup")
  }

  check {
    dependsOn(testStableSemconv)
    dependsOn(testBothSemconv)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
