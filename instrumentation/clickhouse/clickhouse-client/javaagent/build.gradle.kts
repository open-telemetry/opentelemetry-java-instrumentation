plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.clickhouse.client")
    module.set("clickhouse-client")
    versions.set("[0.6,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("com.clickhouse:clickhouse-client:0.6.1")
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("com.clickhouse:clickhouse-client:0.6.1")
  testLibrary("com.clickhouse:clickhouse-http-client:0.6.1")
  testLibrary("org.apache.httpcomponents.client5:httpclient5:5.2.3")
  testInstrumentation(project(":instrumentation:clickhouse:clickhouse-client:javaagent"))
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  if (!(findProperty("testLatestDeps") as Boolean)) {
    check {
      dependsOn(testing.suites)
    }
  }
}
