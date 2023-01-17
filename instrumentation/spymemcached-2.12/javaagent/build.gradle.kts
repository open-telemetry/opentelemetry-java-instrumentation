plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("net.spy")
    module.set("spymemcached")
    versions.set("[2.12.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("net.spy:spymemcached:2.12.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("com.google.guava:guava")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.spymemcached.experimental-span-attributes=true")
  usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
}
