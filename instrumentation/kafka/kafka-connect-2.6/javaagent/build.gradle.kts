import io.opentelemetry.instrumentation.gradle.unitTests

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.kafka")
    module.set("connect-api")
    versions.set("[2.6.0,)")
    assertInverse.set(true)
    excludeInstrumentationName("kafka-clients")
    excludeInstrumentationName("kafka-clients-metrics")
  }
}

dependencies {
  bootstrap(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))
  library("org.apache.kafka:connect-api:2.6.0")
}

testing.suites {
  unitTests {
    dependencies {
      implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
      implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
      implementation(project(":javaagent-bootstrap"))
      implementation(project(":javaagent-extension-api"))
      implementation("org.apache.kafka:connect-api:2.6.0")
    }
  }
}
