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
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }
}
