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
  api(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("javax.servlet:javax.servlet-api:3.0.1")
}
