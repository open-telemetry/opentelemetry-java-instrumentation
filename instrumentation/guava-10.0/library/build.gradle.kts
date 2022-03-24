plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.google.guava:guava:10.0")
  implementation(project(":instrumentation-api-annotation-support"))
}
