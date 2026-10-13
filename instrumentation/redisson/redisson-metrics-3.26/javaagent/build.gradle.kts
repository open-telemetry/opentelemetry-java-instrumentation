plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.redisson")
    module.set("redisson")
    versions.set("[3.26.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:redisson:redisson-metrics-common-2.3:javaagent"))

  library("org.redisson:redisson:3.26.0")

  testImplementation(project(":instrumentation:redisson:redisson-metrics-common-2.3:testing"))
  testInstrumentation(project(":instrumentation:redisson:redisson-metrics-2.3:javaagent"))
  testInstrumentation(project(":instrumentation:redisson:redisson-metrics-3.18:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
