plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.dropwizard")
    module.set("dropwizard-views")
    versions.set("(,3.0.0)")
  }
}

dependencies {
  compileOnly("io.dropwizard:dropwizard-views:0.7.0")

  testImplementation("io.dropwizard:dropwizard-views-freemarker:0.7.0")
  testImplementation("io.dropwizard:dropwizard-views-mustache:0.7.0")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.view-telemetry.enabled=true")
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
