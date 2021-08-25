plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.struts")
    module.set("struts2-core")
    versions.set("[2.3.1,)")
  }
}

dependencies {
  library("org.apache.struts:struts2-core:2.3.1")

  // There was no 2.4 version at all.
  // In version 2.5 Struts Servlet Filter entry point was relocated.
  // This Servlet Filter is relevant only in setting up the test app and it is not used in
  // instrumentation. So fixing Struts library version for the test.
  latestDepTestLibrary("org.apache.struts:struts2-core:2.3.+")

  testImplementation(project(":testing-common"))
  testImplementation("org.eclipse.jetty:jetty-server:8.0.0.v20110901")
  testImplementation("org.eclipse.jetty:jetty-servlet:8.0.0.v20110901")
  testRuntimeOnly("javax.servlet:javax.servlet-api:3.0.1")
  testRuntimeOnly("javax.servlet:jsp-api:2.0")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))
}
