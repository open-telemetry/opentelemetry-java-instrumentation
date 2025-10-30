plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("javax.servlet:javax.servlet-api:3.0.1")

  // FIXME: These dependencies need to be shadowed into the library.
  library(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  library(project(":instrumentation:servlet:servlet-common:javaagent"))
}
