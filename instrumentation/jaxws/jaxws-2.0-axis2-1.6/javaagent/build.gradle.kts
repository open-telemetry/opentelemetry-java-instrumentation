plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.axis2")
    module.set("axis2-jaxws")
    versions.set("[1.6.0,)")
    assertInverse.set(true)
    // version 1.2 depends on org.apache.axis2:axis2-kernel:1.2
    // which depends on org.apache.neethi:neethi:2.0.1 which does not exist
    // version 1.3 depends on org.apache.axis2:axis2-kernel:1.3
    // which depends on org.apache.woden:woden:1.0-incubating-M7b which does not exist
    skip("1.2", "1.3")
  }
}
dependencies {
  val axis2Version = "1.6.0"
  library("org.apache.axis2:axis2-jaxws:$axis2Version")
  testLibrary("org.apache.axis2:axis2-transport-http:$axis2Version")
  testLibrary("org.apache.axis2:axis2-transport-local:$axis2Version")

  testImplementation(project(":instrumentation:jaxws:jaxws-2.0-testing"))

  testInstrumentation(project(":instrumentation:jaxws:jaxws-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxws:jaxws-jws-api-1.1:javaagent"))

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))

  testImplementation("javax.xml.bind:jaxb-api:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-core:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-impl:2.2.11")

  testImplementation("com.sun.xml.ws:jaxws-rt:2.2.8")
  testImplementation("com.sun.xml.ws:jaxws-tools:2.2.8")
}
