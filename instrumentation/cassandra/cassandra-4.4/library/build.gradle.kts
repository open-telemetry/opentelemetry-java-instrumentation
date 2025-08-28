plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.datastax.oss:java-driver-core:4.4.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":instrumentation:cassandra:cassandra-4.4:testing"))
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
    dependsOn(testing.suites.named("testStableSemconv"))
  }
}
