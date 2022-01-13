plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.micrometer:micrometer-core:1.5.0")

  testImplementation(project(":instrumentation:micrometer-1.5:testing"))
}
