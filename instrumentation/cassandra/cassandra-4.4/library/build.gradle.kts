plugins {
  id("otel.library-instrumentation")
}

dependencies {
  if (otelProps.testLatestDeps) {
    library("org.apache.cassandra:java-driver-core:4.18.0")
  } else {
    library("com.datastax.oss:java-driver-core:4.4.0")
  }

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":instrumentation:cassandra:cassandra-4.4:testing"))
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }
}
