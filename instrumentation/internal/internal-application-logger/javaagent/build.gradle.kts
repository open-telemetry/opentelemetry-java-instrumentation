plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.slf4j")
    module.set("slf4j-api")
    versions.set("[1.4.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:internal:internal-application-logger:bootstrap"))

  compileOnly(project(":javaagent-bootstrap"))

  compileOnly("org.slf4j:slf4j-api") {
    version {
      // 1.4.0 introduced the TRACE logging level
      strictly("1.4.0")
    }
  }
}
