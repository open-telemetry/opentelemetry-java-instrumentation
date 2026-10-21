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
    register<JvmTestSuite>("lettuce65Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }

      dependencies {
        implementation(project())
        implementation(project(":instrumentation:lettuce:lettuce-5.1:testing"))
        implementation(project(":instrumentation:reactor:reactor-3.1:library"))
        implementation("io.lettuce:lettuce-core:6.5.0.RELEASE")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  named("lettuce65Test", Test::class) {
    systemProperty("testLatestDeps", true)
    filter {
      includeTestsMatching("io.opentelemetry.instrumentation.lettuce.v5_1.LettuceSyncClientTest.testServerErrorReplyWithInvalidUtf8Details")
    }
  }

  check {
    dependsOn(testing.suites)
  }
}
