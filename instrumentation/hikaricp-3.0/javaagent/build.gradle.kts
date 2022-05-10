plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.zaxxer")
    module.set("HikariCP")
    versions.set("[3.0.0,)")
    // muzzle does not detect PoolStats method references used - some of these methods were introduced in 3.0 and we can't assertInverse
  }
}

dependencies {
  library("com.zaxxer:HikariCP:3.0.0")
}
