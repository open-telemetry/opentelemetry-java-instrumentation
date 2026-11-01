plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.testcontainers-testing")
}

dependencies {
  library("com.oracle.database.jdbc:ucp:11.2.0.4")
  library("com.oracle.database.jdbc:ojdbc8:21.1.0.0")

  testImplementation(project(":instrumentation:oracle-ucp-11.2:testing"))
}
