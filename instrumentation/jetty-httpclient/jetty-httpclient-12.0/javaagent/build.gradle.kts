plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.eclipse.jetty")
    module.set("jetty-client")
    versions.set("[12,)")
  }
}

// We do not want to support alpha or beta version
val jettyVers_base12 = "12.0.0"

dependencies {
  implementation(project(":instrumentation:jetty-httpclient:jetty-httpclient-12.0:library"))

  library("org.eclipse.jetty:jetty-client:$jettyVers_base12")

  testImplementation(project(":instrumentation:jetty-httpclient:jetty-httpclient-12.0:testing"))

  latestDepTestLibrary("org.eclipse.jetty:jetty-client:12.+") // documented limitation
}
