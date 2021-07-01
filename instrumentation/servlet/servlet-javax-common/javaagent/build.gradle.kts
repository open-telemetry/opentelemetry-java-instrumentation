plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("javax.servlet")
    module.set("servlet-api")
    versions.set("(0,)")
  }
  pass {
    group.set("javax.servlet")
    module.set("javax.servlet-api")
    versions.set("[3.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  api(project(":instrumentation:servlet:servlet-javax-common:library"))
  implementation(project(":instrumentation:servlet:servlet-common:javaagent"))

  compileOnly("javax.servlet:servlet-api:2.3")

  // We don't check testLatestDeps for this module since we have coverage in others like servlet-3.0
  testImplementation("org.eclipse.jetty:jetty-server:7.0.0.v20091005")
  testImplementation("org.eclipse.jetty:jetty-servlet:7.0.0.v20091005")
}
