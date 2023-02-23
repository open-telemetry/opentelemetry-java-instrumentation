plugins {
  id("otel.library-instrumentation")
  id("java-test-fixtures")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {

  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
  testImplementation("io.github.netmikey.logunit:logunit-jul:1.1.3")
  testImplementation(testFixtures(project))

  testFixturesImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
  testFixturesImplementation("io.opentelemetry:opentelemetry-api")
  testFixturesImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testFixturesImplementation("org.awaitility:awaitility")
  testFixturesImplementation("org.assertj:assertj-core:3.24.2")
}

testing {
  suites {
    val serialGcTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation(project.dependencies.testFixtures(project))
      }
      targets {
        all {
          testTask {
            jvmArgs = listOf("-XX:+UseSerialGC")
          }
        }
      }
    }
    val parallelGcTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation(project.dependencies.testFixtures(project))
      }
      targets {
        all {
          testTask {
            jvmArgs = listOf("-XX:+UseParallelGC")
          }
        }
      }
    }
    val g1GcTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation(project.dependencies.testFixtures(project))
      }
      targets {
        all {
          testTask {
            jvmArgs = listOf("-XX:+UseG1GC")
          }
        }
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
