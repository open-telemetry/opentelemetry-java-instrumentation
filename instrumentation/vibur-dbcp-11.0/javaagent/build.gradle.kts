plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.vibur")
    module.set("vibur-dbcp")
    versions.set("[11.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.vibur:vibur-dbcp:11.0")

  implementation(project(":instrumentation:vibur-dbcp-11.0:library"))
  implementation(project(":instrumentation:jdbc:javaagent-common"))
  bootstrap(project(":instrumentation:jdbc:bootstrap"))

  testImplementation(project(":instrumentation:vibur-dbcp-11.0:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }
}
