plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.wicket")
    module.set("wicket")
    versions.set("[8.0.0,]")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.apache.wicket:wicket:8.0.0")

  testImplementation(project(":testing-common"))
  testImplementation("org.jsoup:jsoup:1.13.1")
  testImplementation("org.eclipse.jetty:jetty-server:8.0.0.v20110901")
  testImplementation("org.eclipse.jetty:jetty-servlet:8.0.0.v20110901")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
}
