plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.lettuce")
    module.set("lettuce-core")
    versions.set("[5.1.0.RELEASE,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.lettuce:lettuce-core:5.1.0.RELEASE")

  implementation(project(":instrumentation:lettuce:lettuce-5.1:library"))

  testImplementation(project(":instrumentation:lettuce:lettuce-5.1:testing"))

  // Only 5.2+ will have command arguments in the db.statement tag.
  testLibrary("io.lettuce:lettuce-core:5.2.0.RELEASE")
  testInstrumentation(project(":instrumentation:reactor-3.1:javaagent"))
}

tasks {
  test {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
