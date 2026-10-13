plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.redisson")
    module.set("redisson")
    versions.set("[3.18.0,3.26.0)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.redisson:redisson:3.18.0")

  implementation(project(":instrumentation:redisson:redisson-metrics-common-2.3:javaagent"))
  testInstrumentation(project(":instrumentation:redisson:redisson-metrics-2.3:javaagent"))
  testInstrumentation(project(":instrumentation:redisson:redisson-metrics-3.26:javaagent"))
  testImplementation(project(":instrumentation:redisson:redisson-metrics-common-2.3:testing"))
  latestDepTestLibrary("org.redisson:redisson:3.25.+") // see redisson-metrics-3.26 module
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
