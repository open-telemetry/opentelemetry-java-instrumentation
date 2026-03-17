plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("dev.failsafe")
    module.set("failsafe")
    versions.set("[3.0.1,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("dev.failsafe:failsafe:3.0.1")

  implementation(project(":instrumentation:failsafe-3.0:library"))

  testImplementation(project(":instrumentation:failsafe-3.0:testing"))
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.failsafe.enabled=true")
  }
}
