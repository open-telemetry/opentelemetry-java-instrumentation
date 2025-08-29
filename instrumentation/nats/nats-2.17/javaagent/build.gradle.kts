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
  library("io.nats:jnats:2.21.5")

  implementation(project(":instrumentation:nats:nats-2.17:library"))
  testImplementation(project(":instrumentation:nats:nats-2.17:testing"))
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testExperimental by registering(Test::class) {
    filter {
      includeTestsMatching("NatsInstrumentationExperimentalTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.capture-headers=captured-header")

    systemProperty("collectMetadata", collectMetadata)
  }

  test {
    filter {
      excludeTestsMatching("NatsInstrumentationExperimentalTest")
    }

    systemProperty("collectMetadata", collectMetadata)
  }

  check {
    dependsOn(testExperimental)
  }
}
