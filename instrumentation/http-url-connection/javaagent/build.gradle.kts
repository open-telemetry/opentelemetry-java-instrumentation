plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk.set(true)
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testPreviewSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=service.peer")
  }

  check {
    dependsOn(testPreviewSemconv)
  }
}
