plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.pulsar")
    module.set("pulsar-client")
    versions.set("[4.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.pulsar:pulsar-client:4.0.0")
  implementation(project(":instrumentation:pulsar:pulsar-common:javaagent"))
  implementation("org.glassfish.jersey.core:jersey-client:2.42")

  testImplementation("org.testcontainers:pulsar")
  testImplementation("org.apache.pulsar:pulsar-client-admin:4.0.0")
}
