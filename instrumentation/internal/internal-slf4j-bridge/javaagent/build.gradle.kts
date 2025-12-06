plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  // TODO double check
  pass {
    group.set("org.slf4j")
    module.set("slf4j-api")
    versions.set("[2.0.0,)")
    assertInverse.set(true)
  }
}

val latestDepTest = findProperty("testLatestDeps") as Boolean
dependencies {
  bootstrap(project(":instrumentation:internal:internal-slf4j-bridge:bootstrap"))

  compileOnly(project(":javaagent-bootstrap"))

  compileOnly("org.slf4j:slf4j-api") {
    version {
      // 2.0.0 introduced fluent API
      strictly("2.0.0")
    }
  }
}
