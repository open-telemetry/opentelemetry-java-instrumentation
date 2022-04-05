plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.cxf")
    module.set("cxf-rt-frontend-jaxws")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
    extraDependency("javax.servlet:javax.servlet-api:3.0.1")
  }
}

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.apache.cxf:cxf-rt-frontend-jaxws:3.0.0")
  compileOnly("javax.servlet:javax.servlet-api:3.0.1")

  testLibrary("org.apache.cxf:cxf-rt-transports-http:3.0.0")

  testImplementation(project(":instrumentation:jaxws:jaxws-2.0-common-testing"))

  testInstrumentation(project(":instrumentation:jaxws:jaxws-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxws:jaxws-jws-api-1.1:javaagent"))

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))

  testImplementation("javax.xml.ws:jaxws-api:2.3.1")
  testImplementation("javax.xml.bind:jaxb-api:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-core:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-impl:2.2.11")
  testImplementation("javax.activation:javax.activation-api:1.2.0")
  testImplementation("javax.annotation:javax.annotation-api:1.2")
  testImplementation("com.sun.xml.messaging.saaj:saaj-impl:1.5.2")
}
