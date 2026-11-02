import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-testing")
  id("otel.testcontainers-testing")
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
    }
  }
}

testing.suites.executions("test") {
  configureEach {
    jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")
  }

  register("Experimental") {
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
  }
}

testing.suites.executions("testNoReceiveTelemetry") {
  configureEach {
    jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=false")
  }

  configureDefault {
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=false")
  }

  register("Experimental") {
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
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
