plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.redisson")
    module.set("redisson")
    versions.set("[3.0.0,)")
  }
}

dependencies {
  library("org.redisson:redisson:3.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

tasks.named<Test>("test") {
  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
}
