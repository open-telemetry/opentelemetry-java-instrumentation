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

testing {
  suites {
    register<JvmTestSuite>("testNoReceiveTelemetry") {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-kafka-client-3.6:testing"))

        val version = baseVersion("5.0.0").orLatest()
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
  }

  test {
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  val experimentalSuites = testing.suites.withType(JvmTestSuite::class)
    .map { suite ->
      register<Test>("${suite.name}Experimental") {
        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath

        val receiveTelemetryEnabled = suite.name != "testNoReceiveTelemetry"
        jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=$receiveTelemetryEnabled")
        jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
      }
    }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
  }

  val testMessagingPreviewNoReceiveTelemetry = register<Test>("testMessagingPreviewNoReceiveTelemetry") {
    testClassesDirs = sourceSets["testNoReceiveTelemetry"].output.classesDirs
    classpath = sourceSets["testNoReceiveTelemetry"].runtimeClasspath
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=false")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=false")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
  }

  check {
    dependsOn(testing.suites, experimentalSuites, testMessagingPreview, testBothSemconv, testMessagingPreviewNoReceiveTelemetry)
  }
}
