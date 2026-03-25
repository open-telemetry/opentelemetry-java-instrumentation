plugins {
  id("otel.javaagent-instrumentation")
}
muzzle {
  pass {
    group.set("javax.servlet")
    module.set("javax.servlet-api")
    versions.set("[3.0,)")
    assertInverse.set(true)
  }
  fail {
    group.set("javax.servlet")
    module.set("servlet-api")
    versions.set("(,)")
  }
}

dependencies {
  api(project(":instrumentation:servlet:servlet-common:javaagent"))
  api(project(":instrumentation:servlet:servlet-3.0:library"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("javax.servlet:javax.servlet-api:3.0.1")

  testInstrumentation(project(":instrumentation:servlet:servlet-2.2:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
}
