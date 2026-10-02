plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.storm")
    module.set("storm-client")
    versions.set("[2.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.storm:storm-client:2.0.0")

  // LocalCluster, used by the tests, lives in storm-server
  testLibrary("org.apache.storm:storm-server:2.8.9")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  check {
    dependsOn(testMessagingPreview)
  }
}
