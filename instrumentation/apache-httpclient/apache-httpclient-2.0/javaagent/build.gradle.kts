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
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps"))
  }
}