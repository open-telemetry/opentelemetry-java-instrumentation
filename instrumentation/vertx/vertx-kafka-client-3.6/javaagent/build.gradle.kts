import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.unitTests

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

testing.suites {
  unitTests {
    dependencies {
      implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
      implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
      implementation(project(":javaagent-bootstrap"))
      implementation(project(":javaagent-extension-api"))
      implementation("io.vertx:vertx-kafka-client:3.6.0")
    }
  }

  executions("unitTests") {
    configureEach {
      jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")
    }
  }
}
