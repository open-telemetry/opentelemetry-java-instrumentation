plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.zaxxer")
    module.set("HikariCP")
    versions.set("[3.0.0,)")
    // muzzle does not detect PoolStats method references used - some of these methods were introduced in 3.0 and we can't assertInverse

    // 4.0.0 uses a broken slf4j version: the "${slf4j.version}" placeholder is taken literally
    skip("4.0.0")
  }
}

dependencies {
  library("com.zaxxer:HikariCP:3.0.0")

  implementation(project(":instrumentation:hikaricp-3.0:library"))

  testImplementation(project(":instrumentation:hikaricp-3.0:testing"))
}
