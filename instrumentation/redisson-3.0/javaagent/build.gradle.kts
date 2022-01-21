plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.redisson")
    module.set("redisson")
    // in 3.16.8 CommandsData#getPromise() and CommandData#getPromise() return type was changed in
    // a backwards-incompatible way from RPromise to CompletableStage
    versions.set("[3.0.0,3.16.8)")
  }
}

dependencies {
  library("org.redisson:redisson:3.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  latestDepTestLibrary("org.redisson:redisson:3.16.8")
}

tasks.test {
  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
}
