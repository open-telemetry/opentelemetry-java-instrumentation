plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  compileOnly(project(":muzzle")) // For @NoMuzzle
  compileOnly("org.apache.thrift:libthrift:0.21.0")
  testImplementation("org.apache.thrift:libthrift:0.13.0")
  testImplementation(project(":instrumentation:thrift-0.13:testing"))
}

tasks.test {
  systemProperty("testLatestDeps", otelProps.testLatestDeps)
}
