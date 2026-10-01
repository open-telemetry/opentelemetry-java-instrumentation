plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  implementation(project(":instrumentation:jmx-metrics:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation(project(":javaagent-extension-api"))
        implementation(project(":instrumentation:jmx-metrics:library"))
        implementation(project(":declarative-config-bridge"))
        implementation(project(":testing-common"))
        implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
        implementation("io.opentelemetry:opentelemetry-sdk-extension-declarative-config")
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation("io.github.netmikey.logunit:logunit-jul")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
