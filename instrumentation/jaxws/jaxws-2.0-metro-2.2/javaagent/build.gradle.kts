plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.sun.xml.ws")
    module.set("jaxws-rt")
    versions.set("[2.2.0.1,3)")
    // version 2.3.4 depends on org.glassfish.gmbal:gmbal-api-only:4.0.3 which does not exist
    skip("2.3.4")
    assertInverse.set(true)
    extraDependency("javax.servlet:javax.servlet-api:3.0.1")
  }
}

dependencies {
  library("com.sun.xml.ws:jaxws-rt:2.2.0.1")

  compileOnly("javax.servlet:javax.servlet-api:3.0.1")

  testImplementation(project(":instrumentation:jaxws:jaxws-2.0-testing"))

  testInstrumentation(project(":instrumentation:jaxws:jaxws-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxws:jaxws-jws-api-1.1:javaagent"))

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))

  latestDepTestLibrary("com.sun.xml.ws:jaxws-rt:2.+")
}
