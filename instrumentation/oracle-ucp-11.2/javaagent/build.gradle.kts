plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.oracle.database.jdbc")
    module.set("ucp")
    versions.set("[,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.oracle.database.jdbc:ucp:11.2.0.4")
  library("com.oracle.database.jdbc:ojdbc8:12.2.0.1")

  implementation(project(":instrumentation:oracle-ucp-11.2:library"))

  testImplementation(project(":instrumentation:oracle-ucp-11.2:testing"))
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", collectMetadata)
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")

    systemProperty("collectMetadata", collectMetadata)
    systemProperty("metaDataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
