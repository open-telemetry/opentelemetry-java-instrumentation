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
  library("io.nats:jnats:2.17.2")

  implementation(project(":instrumentation:nats:nats-2.17:library"))
  testImplementation(project(":instrumentation:nats:nats-2.17:testing"))
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }
}
