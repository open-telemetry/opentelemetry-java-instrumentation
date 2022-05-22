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
  bootstrap(project(":instrumentation:executors:bootstrap"))

  library("org.eclipse.jetty:jetty-server:8.0.0.v20110901")

  implementation(project(":instrumentation:jetty:jetty-common:javaagent"))
  implementation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))

  testLibrary("org.eclipse.jetty:jetty-servlet:8.0.0.v20110901")

  latestDepTestLibrary("org.eclipse.jetty:jetty-server:10.+") // see jetty-11.0 module
  latestDepTestLibrary("org.eclipse.jetty:jetty-servlet:10.+") // see jetty-11.0 module
}
