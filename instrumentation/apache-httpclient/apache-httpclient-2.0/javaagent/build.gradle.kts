plugins {
  id("otel.javaagent-instrumentation")
}
muzzle {
  pass {
    group.set("commons-httpclient")
    module.set("commons-httpclient")
    versions.set("[2.0,4.0)")
    assertInverse.set(true)
  }
}

dependencies {
  library("commons-httpclient:commons-httpclient:2.0")

  latestDepTestLibrary("commons-httpclient:commons-httpclient:3.+") // see apache-httpclient-4.0 module

  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-5.0:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
    systemProperty("otel.instrumentation.common.peer-service-mapping", "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service")
  }
}
