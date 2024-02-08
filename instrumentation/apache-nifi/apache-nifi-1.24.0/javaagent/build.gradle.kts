plugins {
  id("otel.javaagent-instrumentation")
  id("project-report")
}

muzzle {

  pass {
    group.set("org.apache.nifi")
    module.set("nifi-framework-core")
    versions.set("[1.14.0, 1.24.0)")
  }
  pass {
    group.set("org.apache.nifi")
    module.set("nifi-bin-manager")
    versions.set("[1.14.0, 1.24.0)")
  }
}

dependencies {

  implementation("org.apache.nifi:nifi-framework-core:1.24.0")
  implementation("org.apache.nifi:nifi-bin-manager:1.24.0")
}

tasks.withType<JavaCompile>().configureEach {
  with(options) {
    release.set(8)
  }
}