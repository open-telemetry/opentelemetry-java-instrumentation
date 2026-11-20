plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation-api-incubator"))

  library("io.micrometer:micrometer-core:1.5.0")

  testImplementation(project(":instrumentation:micrometer:micrometer-1.5:testing"))
}
