plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-testing")
  implementation("org.testcontainers:testcontainers-oracle-free")

  compileOnly("com.oracle.database.jdbc:ucp:11.2.0.4")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
