plugins {
  id("otel.javaagent-instrumentation")
}

otelJava {
  // HBase 2.0.x test stack is not reliable on JDK 25+.
  maxJavaVersionForTests.set(JavaVersion.VERSION_24)
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
  library("org.apache.hbase:hbase-client:2.0.0")
  latestDepTestLibrary("org.apache.hbase:hbase-client:2.4.+") // native on-by-default instrumentation after this version
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
  testImplementation("com.google.code.findbugs:annotations:3.0.1")
  testImplementation(project(":instrumentation:hbase:hbase-common:testing"))
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
    // HBase test container binds fixed host ports, so do not run it alongside the default test task.
    mustRunAfter(named("test"))

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
