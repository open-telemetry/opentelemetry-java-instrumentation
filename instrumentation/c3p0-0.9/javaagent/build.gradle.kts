plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("com.mchange")
    module.set("c3p0")
    versions.set("(,)")
  }
}

dependencies {
  // first non pre-release version available on maven central
  library("com.mchange:c3p0:0.9.2")

  implementation(project(":instrumentation:c3p0-0.9:library"))
  implementation(project(":instrumentation:jdbc:javaagent-common"))
  bootstrap(project(":instrumentation:jdbc:bootstrap"))

  testImplementation(project(":instrumentation:c3p0-0.9:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }
}
