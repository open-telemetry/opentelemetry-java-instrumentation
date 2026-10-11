plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.undertow")
    module.set("undertow-core")
    versions.set("[1.4.0.Final,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.undertow:undertow-core:2.0.0.Final")

  bootstrap(project(":instrumentation:executors:bootstrap"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))
  bootstrap(project(":instrumentation:undertow-1.4:bootstrap"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testExceptionSignalLogs by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
    systemProperty("metadataConfig", "otel.semconv.exception.signal.preview=logs")
  }

  check {
    dependsOn(testExceptionSignalLogs)
  }
}

// since 2.3.x, undertow is compiled by JDK 11
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
