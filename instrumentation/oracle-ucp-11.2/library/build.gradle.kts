plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("com.oracle.database.jdbc:ucp:11.2.0.4")
  library("com.oracle.database.jdbc:ojdbc8:12.2.0.1")

  testImplementation(project(":instrumentation:oracle-ucp-11.2:testing"))
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
