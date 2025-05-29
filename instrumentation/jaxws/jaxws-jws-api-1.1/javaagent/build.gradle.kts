plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("javax.jws")
    module.set("javax.jws-api")
    versions.set("[1.1,]")
  }
}

dependencies {
  library("javax.jws:javax.jws-api:1.1")
  implementation(project(":instrumentation:jaxws:jaxws-common:javaagent"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
