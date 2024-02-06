plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("javax.xml.ws")
    module.set("jaxws-api")
    versions.set("[2.0,]")
  }
}

dependencies {
  library("javax.xml.ws:jaxws-api:2.0")
  implementation(project(":instrumentation:jaxws:jaxws-common:javaagent"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
