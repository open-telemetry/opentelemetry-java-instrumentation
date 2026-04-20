plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  fail {
    group.set("javax.ws.rs")
    module.set("jsr311-api")
    versions.set("[,]")
  }
  pass {
    group.set("javax.ws.rs")
    module.set("javax.ws.rs-api")
    versions.set("[,]")
  }
}

dependencies {
  bootstrap(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))

  implementation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-common-2.0:javaagent"))

  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-annotations:javaagent"))

  compileOnly("javax.ws.rs:javax.ws.rs-api:2.0")

  testImplementation("javax.ws.rs:javax.ws.rs-api:2.0")
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")

    systemProperty("collectMetadata", otelProps.collectMetadata)
  }
}
