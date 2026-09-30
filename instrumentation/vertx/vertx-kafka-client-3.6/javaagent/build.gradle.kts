plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-kafka-client")
    versions.set("[3.5.1,)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaClientsInstrumentationModule")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.metrics.KafkaMetricsInstrumentationModule")
  }
}

dependencies {
  bootstrap(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))

  compileOnly("io.vertx:vertx-kafka-client:3.6.0")
  // vertx-codegen is needed for Xlint's annotation checking
  compileOnly("io.vertx:vertx-codegen:3.6.0")
}

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
        implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
        implementation(project(":javaagent-bootstrap"))
        implementation(project(":javaagent-extension-api"))
        implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
        implementation("io.vertx:vertx-kafka-client:3.6.0")
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")
            jvmArgs("-Dotel.semconv-stability.preview=messaging")
          }
        }
      }
    }
  }
}

tasks {
  val legacyUnitTests = register<Test>("legacyUnitTests") {
    val sourceTask = named<Test>("unitTests").get()
    testClassesDirs = sourceTask.testClassesDirs
    classpath = sourceTask.classpath
  }

  check {
    dependsOn(testing.suites, legacyUnitTests)
  }
}

afterEvaluate {
  tasks.named<Test>("legacyUnitTests") {
    val sourceTask = tasks.named<Test>("unitTests").get()
    // Unit tests run without the javaagent or its filtered test classpath.
    jvmArgumentProviders.clear()
    classpath = sourceTask.classpath
    setJvmArgs(sourceTask.jvmArgs.filterNot { it.startsWith("-Dotel.semconv-stability.preview=") })
    setSystemProperties(sourceTask.systemProperties - "otel.semconv-stability.preview")
  }
}
