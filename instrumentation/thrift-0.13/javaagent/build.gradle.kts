plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.thrift")
    module.set("libthrift")
    versions.set("[0.13.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.thrift:libthrift:0.13.0")
  implementation(project(":instrumentation:thrift-0.13:library"))
  testImplementation(project(":instrumentation:thrift-0.13:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=rpc")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=rpc")
  }

  val testBothSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=rpc/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=rpc/dup")
  }

  check {
    dependsOn(testStableSemconv, testBothSemconv)
  }
}
