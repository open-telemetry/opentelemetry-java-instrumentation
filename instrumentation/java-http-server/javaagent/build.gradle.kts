plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:java-http-server:library"))
  testImplementation(project(":instrumentation:java-http-server:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }
}
