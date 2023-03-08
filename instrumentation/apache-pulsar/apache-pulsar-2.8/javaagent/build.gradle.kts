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
  testImplementation("org.testcontainers:pulsar:1.17.1")
  testImplementation("org.apache.pulsar:pulsar-client-admin:2.8.0")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.apache-pulsar.experimental-span-attributes=true")
  usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
}
