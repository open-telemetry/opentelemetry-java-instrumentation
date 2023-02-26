plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
  api("io.r2dbc:r2dbc-proxy:1.0.1.RELEASE")

  testImplementation(project(":instrumentation:r2dbc-1.0:testing"))
}
