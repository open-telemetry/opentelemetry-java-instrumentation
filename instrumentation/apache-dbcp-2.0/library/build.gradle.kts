plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("org.apache.commons:commons-dbcp2:2.0")

  testImplementation(project(":instrumentation:apache-dbcp-2.0:testing"))
}