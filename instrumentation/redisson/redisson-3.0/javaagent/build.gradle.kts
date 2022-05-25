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

  implementation(project(":instrumentation:redisson:redisson-common:javaagent"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":instrumentation:redisson:redisson-common:testing"))

  latestDepTestLibrary("org.redisson:redisson:3.17.1")
}

tasks.test {
  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
}
