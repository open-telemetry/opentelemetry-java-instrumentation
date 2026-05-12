plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.dropwizard")
    module.set("dropwizard-views")
    versions.set("(,3.0.0)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("io.dropwizard:dropwizard-views:0.7.0")

  testImplementation("io.dropwizard:dropwizard-views-freemarker:0.7.0")
  testImplementation("io.dropwizard:dropwizard-views-mustache:0.7.0")
}

tasks.test {
  jvmArgs("-Dotel.instrumentation.common.experimental.view-telemetry.enabled=true")

  systemProperty("collectMetadata", otelProps.collectMetadata)
  systemProperty("metadataConfig", "otel.instrumentation.common.experimental.view-telemetry.enabled=true")
}
