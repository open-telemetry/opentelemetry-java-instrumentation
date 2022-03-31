plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework.boot")
    module.set("spring-boot-actuator-autoconfigure")
    versions.set("[2.0.0.RELEASE,)")
    extraDependency("io.micrometer:micrometer-core:1.5.0")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.springframework.boot:spring-boot-actuator-autoconfigure:2.0.0.RELEASE")
  library("io.micrometer:micrometer-core:1.5.0")

  implementation(project(":instrumentation:micrometer:micrometer-1.5:javaagent"))
}
