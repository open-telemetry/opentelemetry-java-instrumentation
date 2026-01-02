plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("dev.failsafe")
    module.set("failsafe")
    versions.set("[3.0.1,)")
  }
}

dependencies {
  library("dev.failsafe:failsafe:3.0.1")

  implementation(project(":instrumentation:failsafe-3.0:library"))

  testImplementation(project(":instrumentation:failsafe-3.0:testing"))
}
