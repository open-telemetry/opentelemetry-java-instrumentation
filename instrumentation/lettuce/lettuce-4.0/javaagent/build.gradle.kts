plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("biz.paluch.redis")
    module.set("lettuce")
    versions.set("[4.0.Final,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("biz.paluch.redis:lettuce:4.0.Final")

  latestDepTestLibrary("biz.paluch.redis:lettuce:4.+") // see lettuce-5.0 module
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.lettuce.experimental-span-attributes=true")
  usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
}
