plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("io.lettuce:lettuce-core:5.1.0.RELEASE")

  implementation(project(":instrumentation:lettuce:lettuce-common:library"))

  testImplementation(project(":instrumentation:lettuce:lettuce-5.1:testing"))
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
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  check {
    dependsOn(testing.suites)
  }
}
