import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-testing")
  id("otel.testcontainers-testing")
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

testing.suites {
  register<JvmTestSuite>("testNoReceiveTelemetry") {
    dependencies {
      implementation(project(":instrumentation:vertx:vertx-kafka-client-3.6:testing"))

      val version = baseVersion("5.0.0").orLatest()
      implementation("io.vertx:vertx-kafka-client:$version")
      implementation("io.vertx:vertx-codegen:$version")
    }
  }

  executions("test") {
    configureEach {
      jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")
    }

    register("Experimental") {
      jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
