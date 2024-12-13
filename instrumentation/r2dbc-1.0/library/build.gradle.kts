plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
  implementation("io.r2dbc:r2dbc-proxy")

  testImplementation(project(":instrumentation:r2dbc-1.0:testing"))
  testImplementation(project(":instrumentation:reactor:reactor-3.1:library"))
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
