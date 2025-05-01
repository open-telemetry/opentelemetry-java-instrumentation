plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.nats")
    module.set("jnats")
    versions.set("[2.21.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.nats:jnats:2.21.0")

  implementation(project(":instrumentation:nats:nats-2.21:library"))
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
