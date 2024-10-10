plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("jakarta.servlet")
    module.set("jakarta.servlet-api")
    versions.set("[5.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  api(project(":instrumentation:servlet:servlet-common:javaagent"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")
}
