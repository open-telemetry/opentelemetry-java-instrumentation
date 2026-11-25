import io.opentelemetry.instrumentation.gradle.unitTests

plugins {
  id("otel.javaagent-instrumentation")
}

testing.suites {
  unitTests {
    dependencies {
      implementation("io.netty:netty-codec-http:4.0.0.Final")
    }
  }
}

dependencies {
  implementation(project(":instrumentation:netty:netty-common-4.0:library"))
  implementation(project(":instrumentation:netty:netty-common:library"))

  compileOnly("io.netty:netty-codec-http:4.0.0.Final")
}
