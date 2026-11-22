import io.opentelemetry.instrumentation.gradle.unitTests

plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:couchbase:couchbase-common-3.0:javaagent"))
  compileOnly("com.couchbase.client:java-client:3.1.4")
}

testing.suites {
  unitTests {
    dependencies {
      implementation(project(":javaagent-extension-api"))
      implementation("com.couchbase.client:java-client:3.1.4")
    }
  }

  unitTests("protostellarUnitTests") {
    dependencies {
      implementation(project(":javaagent-extension-api"))
      implementation("com.couchbase.client:java-client:3.4.3")
    }
  }
}
