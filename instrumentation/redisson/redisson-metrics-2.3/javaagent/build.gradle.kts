plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.redisson")
    module.set("redisson")
    versions.set("[2.3.0,3.18.0)")
    // 0.9.0 exposes matching classes.
    skip("0.9.0")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.redisson:redisson:2.3.0")

  testImplementation(project(":instrumentation:redisson:redisson-common-3.0:testing"))

  latestDepTestLibrary("org.redisson:redisson:3.17.+") // documented limitation
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
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
