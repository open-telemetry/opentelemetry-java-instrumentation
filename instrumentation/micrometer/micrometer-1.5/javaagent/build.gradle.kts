plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.micrometer")
    module.set("micrometer-core")
    versions.set("[1.5.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.micrometer:micrometer-core:1.5.0")
}

// TODO: disabled by default, since not all instruments are implemented
tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.micrometer.enabled=true")
}
