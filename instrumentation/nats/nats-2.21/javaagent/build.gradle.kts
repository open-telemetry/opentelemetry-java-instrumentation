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
  library("io.nats:jnats:2.21.0")

  implementation(project(":instrumentation:nats:nats-2.21:library"))
  testImplementation(project(":instrumentation:nats:nats-2.21:testing"))
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

tasks {
  val testExperimental by registering(Test::class) {
    filter {
      includeTestsMatching("NatsInstrumentationExperimentalTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.capture-headers=captured-header")

    systemProperty("collectMetadata", collectMetadata)
  }

  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    filter {
      excludeTestsMatching("NatsInstrumentationExperimentalTest")
    }

    systemProperty("collectMetadata", collectMetadata)
  }

  check {
    dependsOn(testExperimental)
  }
}
