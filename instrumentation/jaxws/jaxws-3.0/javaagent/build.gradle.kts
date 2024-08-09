plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("jakarta.xml.ws")
    module.set("jakarta.xml.ws-api")
    versions.set("[3.0,]")
  }
}

dependencies {
  library("jakarta.xml.ws:jakarta.xml.ws-api:3.0.0")
  implementation(project(":instrumentation:jaxws:jaxws-common:javaagent"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
