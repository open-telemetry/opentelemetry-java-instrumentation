plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
  implementation("io.r2dbc:r2dbc-proxy")

  testImplementation(project(":instrumentation:r2dbc-1.0:testing"))
  testImplementation(project(":instrumentation:reactor:reactor-3.1:library"))
}

testing {
  suites {
    val testStableSemconv by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.opt-in=database")
          }
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  check {
    dependsOn(testing.suites)
  }
}
