plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
  implementation("io.r2dbc:r2dbc-proxy") {
    exclude(group = "io.r2dbc", module = "r2dbc-spi")
  }

  testImplementation(project(":instrumentation:r2dbc-1.0:testing"))
  testImplementation(project(":instrumentation:reactor:reactor-3.1:library"))
  testImplementation("org.testcontainers:testcontainers")
}

sourceSets {
  test {
    java.srcDir("src/testSpiCompatibility/java")
    java.srcDir(project(":instrumentation:r2dbc-1.0:testing").file("src/testSpiCompatibility/java"))
  }
}

testing {
  suites {
    register<JvmTestSuite>("testSpi09") {
      sources {
        java {
          setSrcDirs(
            listOf(
              "src/testSpi09/java",
              "src/testSpiCompatibility/java",
              project(":instrumentation:r2dbc-1.0:testing").file("src/testSpiCompatibility/java"),
            )
          )
        }
      }
      dependencies {
        implementation(files(sourceSets.main.get().output))
        implementation("io.r2dbc:r2dbc-h2:0.9.0.RELEASE")
        implementation("io.r2dbc:r2dbc-proxy") {
          exclude(group = "io.r2dbc", module = "r2dbc-spi")
        }
        implementation(project(":instrumentation:reactor:reactor-3.1:library"))
      }
    }
  }
}

tasks {
  named<Test>("test") {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
  check {
    dependsOn(testing.suites)
  }
}
