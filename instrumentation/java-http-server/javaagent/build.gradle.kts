plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk()
  }
}

dependencies {
  implementation(project(":instrumentation:java-http-server:library"))
  testImplementation(project(":instrumentation:java-http-server:testing"))
}
