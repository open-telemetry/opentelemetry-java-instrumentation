plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.github.resilience4j")
    module.set("resilience4j-circuitbreaker")
    versions.set("[0.15.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.github.resilience4j:resilience4j-circuitbreaker:0.15.0")
}
