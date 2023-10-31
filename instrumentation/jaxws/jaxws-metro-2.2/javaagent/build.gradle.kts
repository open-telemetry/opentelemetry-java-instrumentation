plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.sun.xml.ws")
    module.set("jaxws-rt")
    versions.set("[2.2.0.1,)")
    // version 2.3.4 depends on org.glassfish.gmbal:gmbal-api-only:4.0.3 which does not exist
    skip("2.3.4")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("com.sun.xml.ws:jaxws-rt:2.2.0.1")
  // early versions of streambuffer depend on latest release of org.jvnet.staxex:stax-ex
  // which doesn't work with java 8
  library("com.sun.xml.stream.buffer:streambuffer:1.4")

  compileOnly("javax.xml.ws:jaxws-api:2.0")
  compileOnly("jakarta.xml.ws:jakarta.xml.ws-api:3.0.0")
}
