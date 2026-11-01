plugins {
  id("otel.javaagent-instrumentation")
  id("otel.testcontainers-testing")
}

muzzle {
  pass {
    group.set("com.clickhouse")
    module.set("client-v2")
    versions.set("[0.6.4,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:clickhouse:clickhouse-client-common-0.5:javaagent"))
  library("com.clickhouse:client-v2:0.8.0")
  testInstrumentation(project(":instrumentation:clickhouse:clickhouse-client-v1-0.5:javaagent"))
}
