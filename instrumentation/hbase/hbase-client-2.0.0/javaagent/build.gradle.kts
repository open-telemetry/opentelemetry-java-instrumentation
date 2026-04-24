plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.hbase")
    module.set("hbase-client")
    versions.set("[2.0.0, 2.5.0)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:hbase:hbase-common:javaagent"))

  library("org.apache.hbase:hbase-client:2.0.0")
  testLibrary("org.apache.hbase:hbase-client:2.3.7")
  latestDepTestLibrary("org.apache.hbase:hbase-client:2.4.+")
  testImplementation("com.google.code.findbugs:annotations:3.0.1")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
    jvmArgs(
      "-Dotel.javaagent.exclude-classes=com.google.protobuf.*,com.fasterxml.jackson.*,com.google.common.*,ch.qos.logback.*,javax.xml.*",
    )
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs(
      "-Dotel.semconv-stability.opt-in=database",
      "-Dotel.javaagent.exclude-classes=com.google.protobuf.*,com.fasterxml.jackson.*,com.google.common.*,ch.qos.logback.*,javax.xml.*",
    )
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  check {
    dependsOn(testStableSemconv)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
