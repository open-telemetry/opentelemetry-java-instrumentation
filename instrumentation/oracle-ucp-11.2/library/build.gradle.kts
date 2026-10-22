plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("com.oracle.database.jdbc:ucp:11.2.0.4")
  library("com.oracle.database.jdbc:ojdbc8:21.1.0.0")

  testImplementation(project(":instrumentation:oracle-ucp-11.2:testing"))
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
