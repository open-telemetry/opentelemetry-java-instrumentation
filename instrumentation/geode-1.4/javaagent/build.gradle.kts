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
  compileOnly("net.java.dev.jna:jna:4.1.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

tasks {
  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("collectMetadata", collectMetadata)
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  test {
    systemProperty("collectMetadata", collectMetadata)
  }

  check {
    dependsOn(testStableSemconv)
  }
}
