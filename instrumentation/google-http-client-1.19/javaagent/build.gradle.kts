plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.google.http-client")
    module.set("google-http-client")

    // 1.19.0 is the first release.  The versions before are betas and RCs
    versions.set("[1.19.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.google.http-client:google-http-client:1.19.0")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", findProperty("collectMetadata"))
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
