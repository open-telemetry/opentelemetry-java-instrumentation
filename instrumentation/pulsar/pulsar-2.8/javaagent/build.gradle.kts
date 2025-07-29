plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.pulsar")
    module.set("pulsar-client")
    versions.set("[2.8.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.pulsar:pulsar-client:2.8.0")

  testImplementation("javax.annotation:javax.annotation-api:1.3.2")
  testImplementation("org.testcontainers:pulsar")
  testImplementation("org.apache.pulsar:pulsar-client-admin:2.8.0")
}

tasks {
  val testReceiveSpanDisabled by registering(Test::class) {
    filter {
      includeTestsMatching("PulsarClientSuppressReceiveSpansTest")
    }
    include("**/PulsarClientSuppressReceiveSpansTest.*")
  }

  test {
    filter {
      excludeTestsMatching("PulsarClientSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(testReceiveSpanDisabled)
  }
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.pulsar.experimental-span-attributes=true")
  usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
}
