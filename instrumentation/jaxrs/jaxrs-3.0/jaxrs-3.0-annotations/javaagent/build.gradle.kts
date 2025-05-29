plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("jakarta.ws.rs")
    module.set("jakarta.ws.rs-api")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))

  implementation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-common:javaagent"))

  compileOnly("jakarta.ws.rs:jakarta.ws.rs-api:3.0.0")

  testImplementation("jakarta.ws.rs:jakarta.ws.rs-api:3.0.0")
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }
}
