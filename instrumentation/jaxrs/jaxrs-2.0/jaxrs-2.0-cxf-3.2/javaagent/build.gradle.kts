plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  // Cant assert fails because muzzle assumes all instrumentations will fail
  // Instrumentations in jaxrs-2.0-common will pass
  pass {
    group.set("org.apache.cxf")
    module.set("cxf-rt-frontend-jaxrs")
    versions.set("[3.2,4)")
    extraDependency("javax.servlet:javax.servlet-api:3.1.0")
  }
  pass {
    group.set("org.apache.tomee")
    module.set("openejb-cxf-rs")
    // earlier versions of tomee use cxf older than 3.2
    versions.set("(8,)")
    extraDependency("javax.ws.rs:javax.ws.rs-api:2.0")
    extraDependency("javax.servlet:javax.servlet-api:3.1.0")
  }
}

dependencies {
  bootstrap(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))

  compileOnly("javax.ws.rs:javax.ws.rs-api:2.0")
  compileOnly("javax.servlet:javax.servlet-api:3.1.0")
  library("org.apache.cxf:cxf-rt-frontend-jaxrs:3.2.0")

  implementation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-common:javaagent"))

  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-annotations:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-jersey-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-resteasy-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-resteasy-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-jersey-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-resteasy-6.0:javaagent"))

  testImplementation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-common:testing"))
  testImplementation("javax.xml.bind:jaxb-api:2.2.3")
  testImplementation("org.eclipse.jetty:jetty-webapp:9.4.6.v20170531")

  testLibrary("org.apache.cxf:cxf-rt-transports-http-jetty:3.2.0")
  testLibrary("org.apache.cxf:cxf-rt-ws-policy:3.2.0")

  latestDepTestLibrary("org.eclipse.jetty:jetty-webapp:10.+") // documented limitation
  latestDepTestLibrary("org.apache.cxf:cxf-rt-frontend-jaxrs:3.+") // documented limitation
  latestDepTestLibrary("org.apache.cxf:cxf-rt-transports-http-jetty:3.+") // documented limitation
  latestDepTestLibrary("org.apache.cxf:cxf-rt-ws-policy:3.+") // documented limitation
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)

  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.jaxrs.experimental-span-attributes=true")
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
