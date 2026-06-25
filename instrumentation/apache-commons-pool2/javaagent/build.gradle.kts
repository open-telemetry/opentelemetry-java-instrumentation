plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.commons")
    module.set("commons-pool2")
    versions.set("[2.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.commons:commons-pool2:2.0")

  implementation(project(":instrumentation:apache-commons-pool2:library"))

  testImplementation(project(":instrumentation:apache-commons-pool2:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
