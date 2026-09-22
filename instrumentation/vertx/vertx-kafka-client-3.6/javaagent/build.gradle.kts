plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-kafka-client")
    versions.set("[3.5.1,)")
    assertInverse.set(true)
    excludeInstrumentationName("kafka-clients")
    excludeInstrumentationName("kafka-clients-metrics")
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
  check {
    dependsOn(testing.suites)
  }
}
