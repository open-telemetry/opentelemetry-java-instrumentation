plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.redisson")
    module.set("redisson")
    versions.set("[3.0.0,3.17.2)")
  }
}

dependencies {
  library("org.redisson:redisson:3.0.0")

  // TODO (trask) split out instrumentation into two modules and support 3.17.2+
  latestDepTestLibrary("org.redisson:redisson:3.17.1")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

tasks.test {
  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
}
