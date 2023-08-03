plugins {
  id("otel.library-instrumentation")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.r2dbc.v1_0")

dependencies {
  library("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
  implementation("io.r2dbc:r2dbc-proxy:1.0.1.RELEASE")

  testImplementation(project(":instrumentation:r2dbc-1.0:testing"))
  testImplementation(project(":instrumentation:reactor:reactor-3.1:library"))
}

tasks.withType<Test>().configureEach {
  usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
}
