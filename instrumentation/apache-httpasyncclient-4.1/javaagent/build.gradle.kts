plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.httpcomponents")
    module.set("httpasyncclient")
    // 4.0 and 4.0.1 don't copy over the traceparent (etc) http headers on redirect
    versions.set("[4.1,)")
    // 4.0 and 4.0.1 pass muzzle (same API) but are behaviorally broken, so skip them in the inverse check
    skip("4.0", "4.0.1")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.httpcomponents:httpasyncclient:4.1")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
