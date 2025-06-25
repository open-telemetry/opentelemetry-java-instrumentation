plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.activej")
    module.set("activej-http")
    versions.set("[6.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.activej:activej-http:6.0-rc2")
  latestDepTestLibrary("io.activej:activej-http:6.+") // documented limitation, can be removed when there is a non rc version in 6.x series
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  test {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
    systemProperty("collectSpans", true)
  }
}
