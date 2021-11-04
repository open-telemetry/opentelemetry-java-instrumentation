plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.eclipse.jetty")
    module.set("jetty-server")
    // Jetty 11+ is covered by jetty-11.0 module
    versions.set("[8.0.0.v20110901,11)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.eclipse.jetty:jetty-server:8.0.0.v20110901")
  implementation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  implementation(project(":instrumentation:jetty:jetty-common:javaagent"))

  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))

  testLibrary("org.eclipse.jetty:jetty-servlet:8.0.0.v20110901")
  testLibrary("org.eclipse.jetty:jetty-continuation:8.0.0.v20110901")

  // Jetty 10 seems to refuse to run on java8.
  // TODO: we need to setup separate test for Jetty 10 when that is released.
  latestDepTestLibrary("org.eclipse.jetty:jetty-server:9.+")
  latestDepTestLibrary("org.eclipse.jetty:jetty-servlet:9.+")
  latestDepTestLibrary("org.eclipse.jetty:jetty-continuation:9.+")
}
