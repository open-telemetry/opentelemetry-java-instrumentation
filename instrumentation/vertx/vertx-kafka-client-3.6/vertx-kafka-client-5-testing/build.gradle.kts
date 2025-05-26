plugins {
  id("otel.javaagent-testing")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  library("io.vertx:vertx-kafka-client:5.0.0")
  // vertx-codegen is needed for Xlint's annotation checking
  library("io.vertx:vertx-codegen:5.0.0")

  testImplementation(project(":instrumentation:vertx:vertx-kafka-client-3.6:testing"))

  testInstrumentation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-kafka-client-3.6:javaagent"))
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

testing {
  suites {
    val testNoReceiveTelemetry by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-kafka-client-3.6:testing"))

        val version = if (latestDepTest) "latest.release" else "5.0.0"
        implementation("io.vertx:vertx-kafka-client:$version")
        implementation("io.vertx:vertx-codegen:$version")
      }

      targets {
        all {
          testTask.configure {
            usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

            systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)

            jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=false")
            jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=false")
          }
        }
      }
    }
  }
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    systemProperty("testLatestDeps", latestDepTest)

    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(testing.suites)
  }
}
