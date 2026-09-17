plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("org.apache.commons:commons-pool2:2.0")

  testImplementation(project(":instrumentation:apache-commons-pool-2.0:testing"))
}
