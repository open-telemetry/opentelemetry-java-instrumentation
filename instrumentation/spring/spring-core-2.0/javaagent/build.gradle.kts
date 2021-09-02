plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-core")
    versions.set("[2.0,]")
  }
}

dependencies {
  library("org.springframework:spring-core:2.0")

  // 3.0 introduces submit() methods
  // 4.0 introduces submitListenable() methods
  testLibrary("org.springframework:spring-core:4.0.0.RELEASE")
}
