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
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.apache.struts:struts2-core:2.3.1")

  testImplementation(project(":testing-common"))
  testImplementation("org.eclipse.jetty:jetty-server:8.0.0.v20110901")
  testImplementation("org.eclipse.jetty:jetty-servlet:8.0.0.v20110901")
  testRuntimeOnly("javax.servlet:javax.servlet-api:3.0.1")
  testRuntimeOnly("javax.servlet:jsp-api:2.0")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))
}
