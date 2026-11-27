plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.restlet")
    module.set("org.restlet")
    versions.set("[2.5.0,3.0.0-m1)")
    assertInverse.set(true)
  }
}

dependencies {
  api(project(":instrumentation:restlet:restlet-2.5:library"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.restlet:org.restlet:2.5.0")
  implementation(project(":instrumentation:restlet:restlet-2.5:library"))

  testImplementation(project(":instrumentation:restlet:restlet-2.5:testing"))
  testLibrary("org.restlet:org.restlet.ext.spring:2.5.0")
  if (!otelProps.testLatestDeps) {
    // Restlet 2.7 includes the Jetty connector in core.
    testImplementation("org.restlet:org.restlet.ext.jetty:2.5.0")
  }
  latestDepTestLibrary("org.restlet:org.restlet:2.+")
  latestDepTestLibrary("org.restlet:org.restlet.ext.spring:2.+")
}
