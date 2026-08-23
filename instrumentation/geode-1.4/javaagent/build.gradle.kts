plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.geode")
    module.set("geode-core")
    versions.set("[1.4.0,)")
  }
}

dependencies {
  library("org.apache.geode:geode-core:1.4.0") {
    // jna 4.0.0 has invalid ZIP64 headers, broken on JDK 23+ (JDK-8313765)
    exclude(group = "net.java.dev.jna", module = "jna")
  }
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  // A Geode client cannot connect to an older server. The apachegeode/geode image publishes
  // nothing newer than 1.15.1, so the client cannot be tested past that version either.
  latestDepTestLibrary("org.apache.geode:geode-core:1.15.1") // documented limitation
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
