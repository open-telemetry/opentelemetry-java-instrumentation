plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.clickhouse.client")
    module.set("clickhouse-client")
    versions.set("[0.5.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:clickhouse:clickhouse-client-common:javaagent"))
  compileOnly("com.clickhouse:clickhouse-client:0.5.0")

  testImplementation("com.google.guava:guava")
  testImplementation(project(":instrumentation:clickhouse:testing"))
  testLibrary("com.clickhouse:clickhouse-client:0.5.0")
  testLibrary("com.clickhouse:clickhouse-http-client:0.5.0")
  testLibrary("org.apache.httpcomponents.client5:httpclient5:5.2.3")
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", collectMetadata)
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")

    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
    systemProperty("collectMetadata", collectMetadata)
  }

  check {
    dependsOn(testStableSemconv)
  }
}
