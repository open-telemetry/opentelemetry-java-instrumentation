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
    versions.set("(,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.httpcomponents:httpclient:4.1")
}
