plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.nats")
    module.set("jnats")
    versions.set("[2.17.2,)")

    // Could not find io.nats:nats-parent:1.0-SNAPSHOT
    skip("0.5.0", "0.5.1")

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
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  val testExperimental by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("NatsInstrumentationExperimentalTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.capture-headers=captured-header")
  }

  test {
    filter {
      excludeTestsMatching("NatsInstrumentationExperimentalTest")
    }
  }

  check {
    dependsOn(testExperimental)
  }
}
