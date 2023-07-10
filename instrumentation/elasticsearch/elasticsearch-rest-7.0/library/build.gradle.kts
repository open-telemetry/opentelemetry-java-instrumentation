plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.elasticsearch.client:elasticsearch-rest-client:7.0.0")
  implementation("net.bytebuddy:byte-buddy")
  implementation(project(":instrumentation:elasticsearch:elasticsearch-rest-common:library"))

  testImplementation("org.apache.logging.log4j:log4j-core:2.11.0")
  testImplementation("org.apache.logging.log4j:log4j-api:2.11.0")
  testImplementation("com.fasterxml.jackson.core:jackson-databind")

  testImplementation("org.testcontainers:elasticsearch")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
