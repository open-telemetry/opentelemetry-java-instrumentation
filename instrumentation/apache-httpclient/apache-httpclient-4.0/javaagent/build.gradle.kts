plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  fail {
    group.set("commons-httpclient")
    module.set("commons-httpclient")
    versions.set("[,4.0)")
  }
  pass {
    group.set("org.apache.httpcomponents")
    module.set("httpclient")
    versions.set("[4.0,)")
    assertInverse.set(true)
  }
  pass {
    // We want to support the dropwizard clients too.
    group.set("io.dropwizard")
    module.set("dropwizard-client")
    versions.set("(,3.0.0)")
    assertInverse.set(true)
    // Could not find com.google.code.findbugs:jsr305:.
    skip("3.0.2", "4.0.2")
  }
}

dependencies {
  library("org.apache.httpcomponents:httpclient:4.0")
  testCompileOnly("net.jcip:jcip-annotations:1.0")
}

tasks {
  test {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }
}
