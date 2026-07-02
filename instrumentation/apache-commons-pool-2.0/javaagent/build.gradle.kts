plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.commons")
    module.set("commons-pool2")
    versions.set("[2.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.commons:commons-pool2:2.0")

  implementation(project(":instrumentation:apache-commons-pool-2.0:library"))

  testImplementation(project(":instrumentation:apache-commons-pool-2.0:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }
}
