plugins {
  id("otel.javaagent-testing")
}

otelJava {
  // HBase 2.0.x test stack is not reliable on JDK 25+.
  maxJavaVersionForTests.set(JavaVersion.VERSION_24)
}

dependencies {
  library("org.apache.hbase:hbase-shaded-client:2.0.0")

  testInstrumentation(project(":instrumentation:hbase:hbase-client-2.0:javaagent"))
  testImplementation(project(":instrumentation:hbase:hbase-common:testing"))
  latestDepTestLibrary("org.apache.hbase:hbase-shaded-client:2.4.+") // documented limitation
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    // HBase test container binds fixed host ports, so keep shaded tests after the javaagent HBase tests.
    mustRunAfter(
      ":instrumentation:hbase:hbase-client-2.0:javaagent:test",
      ":instrumentation:hbase:hbase-client-2.0:javaagent:testStableSemconv",
    )
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
