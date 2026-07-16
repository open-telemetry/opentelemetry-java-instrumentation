plugins {
  id("otel.javaagent-testing")
}

dependencies {
  library("io.vertx:vertx-kafka-client:4.0.0")
  // vertx-codegen is needed for Xlint's annotation checking
  library("io.vertx:vertx-codegen:4.0.0")

  testImplementation(project(":instrumentation:vertx:vertx-kafka-client-3.6:testing"))

  testInstrumentation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-kafka-client-3.6:javaagent"))

  latestDepTestLibrary("io.vertx:vertx-kafka-client:4.+") // see vertx-kafka-client-5-testing module
  latestDepTestLibrary("io.vertx:vertx-codegen:4.+") // see vertx-kafka-client-5-testing module
}

testing {
  suites {
    register<JvmTestSuite>("testNoReceiveTelemetry") {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-kafka-client-3.6:testing"))

        val version = baseVersion("4.0.0").orLatest("4.+")
        implementation("io.vertx:vertx-kafka-client:$version")
        implementation("io.vertx:vertx-codegen:$version")
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=false")
            jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=false")
          }
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  test {
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
  }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
  }

  check {
    dependsOn(testing.suites, testExperimental, testV3Preview)
  }
}
