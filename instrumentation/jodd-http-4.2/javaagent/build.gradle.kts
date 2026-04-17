plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.jodd")
    module.set("jodd-http")
    versions.set("[4.1.1,)")
    assertInverse.set(true)
  }
}

dependencies {
  // 4.1.1 is the first version with HttpBase#headerOverwrite used by header injection
  library("org.jodd:jodd-http:4.1.1")

  testImplementation(project(":instrumentation:jodd-http-4.2:javaagent"))
  testImplementation(project(":instrumentation-api-incubator"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
