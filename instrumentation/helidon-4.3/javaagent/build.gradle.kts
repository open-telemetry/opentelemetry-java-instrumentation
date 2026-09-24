plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.helidon.webserver")
    module.set("helidon-webserver")
    versions.set("[4.3.0,)")
    assertInverse.set(true)
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_21)
  if (otelProps.testLatestDeps) {
    maxJavaVersionSupported.set(JavaVersion.VERSION_27)
  }
}

dependencies {
  library("io.helidon.webserver:helidon-webserver:4.3.0")
  implementation(project(":instrumentation:helidon-4.3:library"))
  testImplementation(project(":instrumentation:helidon-4.3:testing"))
  // 27.0.0 requires java 27, limit the latest dep version for now
  latestDepTestLibrary("io.helidon.webserver:helidon-webserver:27.0.0") // documented limitation
}

tasks.test {
  systemProperty("collectMetadata", otelProps.collectMetadata)
}
