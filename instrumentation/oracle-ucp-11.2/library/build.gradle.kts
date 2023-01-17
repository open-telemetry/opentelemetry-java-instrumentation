plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("com.oracle.database.jdbc:ucp:11.2.0.4")

  testImplementation(project(":instrumentation:oracle-ucp-11.2:testing"))
}
