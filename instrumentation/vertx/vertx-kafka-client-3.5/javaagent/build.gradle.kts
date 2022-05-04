plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  bootstrap(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common:library"))

  library("io.vertx:vertx-kafka-client:3.5.0")

  // vertx-codegen and vertx-docgen dependencies are needed for Xlint's annotation checking
  library("io.vertx:vertx-codegen:3.0.0")
  testLibrary("io.vertx:vertx-docgen:3.0.0")

  // vertx-kafka-client 3.5 uses kafka-clients 0.10.2.1 by default, need to bump it to make instrumentation work
  testImplementation("org.apache.kafka:kafka-clients:0.11.0.0")

  testImplementation(project(":instrumentation:vertx:vertx-kafka-client-3.5:testing"))

  testInstrumentation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))
}

testing {
  suites {
    val testNoReceiveTelemetry by registering(JvmTestSuite::class) {
      dependencies {
        implementation("io.vertx:vertx-kafka-client:3.5.0")
        implementation("io.vertx:vertx-codegen:3.0.0")
        implementation("io.vertx:vertx-docgen:3.0.0")
        implementation("org.apache.kafka:kafka-clients:0.11.0.0")
        implementation(project(":instrumentation:vertx:vertx-kafka-client-3.5:testing"))
      }

      targets {
        all {
          testTask.configure {
            usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

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

    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(testing.suites)
  }
}
