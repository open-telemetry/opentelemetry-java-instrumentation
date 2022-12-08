plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-web")
    versions.set("[6.0.0)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("org.springframework:spring-web:6.0.0")
}
