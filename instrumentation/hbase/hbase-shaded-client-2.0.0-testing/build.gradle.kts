plugins {
  id("otel.javaagent-testing")
}

dependencies {
  library("org.apache.hbase:hbase-shaded-client:2.0.0")

  testInstrumentation(project(":instrumentation:hbase:hbase-client-2.0.0:javaagent"))
  testImplementation(project(":instrumentation:hbase:hbase-common:testing"))
  latestDepTestLibrary("org.apache.hbase:hbase-shaded-client:2.4.+")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    jvmArgs(
      "-Dotel.javaagent.exclude-classes=com.google.protobuf.*,com.fasterxml.jackson.*,com.google.common.*,ch.qos.logback.*,javax.xml.*",
    )
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
