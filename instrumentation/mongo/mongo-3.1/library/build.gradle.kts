plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("org.mongodb:mongo-java-driver:3.1.0")

  testImplementation(project(":instrumentation:mongo:mongo-3.1:testing"))
}

tasks {
  named<Test>("test") {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
