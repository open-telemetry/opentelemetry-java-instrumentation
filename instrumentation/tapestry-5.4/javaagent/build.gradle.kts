plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.tapestry")
    module.set("tapestry-core")
    versions.set("[5.4.0,)")
    assertInverse.set(true)
  }
}

otelJava {
  maxJavaVersionForTests.set(JavaVersion.VERSION_1_8)
}

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.apache.tapestry:tapestry-core:5.4.0")

  testImplementation("org.eclipse.jetty:jetty-webapp:8.0.0.v20110901")
  testImplementation("org.jsoup:jsoup:1.13.1")
  testImplementation("javax.annotation:javax.annotation-api:1.3.2")

  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
}
