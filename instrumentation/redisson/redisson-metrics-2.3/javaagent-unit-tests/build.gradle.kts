plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:redisson:redisson-metrics-2.3:javaagent"))
  testImplementation("org.redisson:redisson:2.3.0")
}

testing {
  suites {
    register<JvmTestSuite>("redisson251Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }

      dependencies {
        implementation(project(":instrumentation:redisson:redisson-metrics-2.3:javaagent"))
        implementation("org.redisson:redisson:2.5.1")
      }
    }

    register<JvmTestSuite>("redisson311Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }

      dependencies {
        implementation(project(":instrumentation:redisson:redisson-metrics-2.3:javaagent"))
        implementation("org.redisson:redisson:${baseVersion("3.11.0").orLatest("3.11.+")}")
      }
    }

    register<JvmTestSuite>("redisson315Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }

      dependencies {
        implementation(project(":instrumentation:redisson:redisson-metrics-2.3:javaagent"))
        implementation("org.redisson:redisson:3.15.6")
      }
    }

    register<JvmTestSuite>("redisson317Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }

      dependencies {
        implementation(project(":instrumentation:redisson:redisson-metrics-2.3:javaagent"))
        implementation("org.redisson:redisson:${baseVersion("3.17.0").orLatest("3.17.+")}")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  check {
    dependsOn(testing.suites)
  }
}
