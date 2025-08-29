plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.nats:jnats:2.17.7")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":instrumentation:nats:nats-2.17:testing"))
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
