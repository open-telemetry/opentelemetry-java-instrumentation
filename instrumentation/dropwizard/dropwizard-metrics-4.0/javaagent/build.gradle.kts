plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.dropwizard.metrics")
    module.set("metrics-core")
    versions.set("[4.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.dropwizard.metrics:metrics-core:4.0.0")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.dropwizard-metrics.enabled=true")
}