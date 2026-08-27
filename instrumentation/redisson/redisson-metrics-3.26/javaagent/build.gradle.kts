plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.redisson")
    module.set("redisson")
    versions.set("[3.26.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:redisson:redisson-metrics-common-3.18:javaagent"))

  library("org.redisson:redisson:3.26.0")

  testImplementation(project(":instrumentation:redisson:redisson-metrics-common-3.18:testing"))
  testInstrumentation(project(":instrumentation:redisson:redisson-metrics-3.18:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
