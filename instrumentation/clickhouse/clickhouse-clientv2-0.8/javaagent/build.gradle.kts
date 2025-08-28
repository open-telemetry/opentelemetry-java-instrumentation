plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.clickhouse")
    module.set("client-v2")
    versions.set("[0.6.4,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:clickhouse:clickhouse-client-common:javaagent"))
  compileOnly("com.clickhouse:client-v2:0.8.0")

  testImplementation(project(":instrumentation:clickhouse:testing"))
  testLibrary("com.clickhouse:client-v2:0.8.0")
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", collectMetadata)
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")

    systemProperty("metaDataConfig", "otel.semconv-stability.opt-in=database")
    systemProperty("collectMetadata", collectMetadata)
  }

  check {
    dependsOn(testStableSemconv)
  }
}
