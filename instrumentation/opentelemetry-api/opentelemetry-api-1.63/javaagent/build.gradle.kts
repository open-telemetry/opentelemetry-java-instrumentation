plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "v1_63"))

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.27:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.40:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.42:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.47:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.50:javaagent"))
}

configurations.configureEach {
  if (name.endsWith("testRuntimeClasspath", true) || name.endsWith("testCompileClasspath", true)) {
    resolutionStrategy {
      force("io.opentelemetry:opentelemetry-api:1.63.0")
      force("io.opentelemetry:opentelemetry-api-incubator:1.63.0-alpha")
      // use older SDK test artifacts that work with opentelemetry-api-incubator:1.63.0-alpha
      force("io.opentelemetry:opentelemetry-sdk:1.63.0")
      force("io.opentelemetry:opentelemetry-sdk-logs:1.63.0")
      force("io.opentelemetry:opentelemetry-sdk-testing:1.63.0")
    }
  }
}

testing {
  suites {
    register<JvmTestSuite>("incubatorTest") {
      dependencies {
        implementation("io.opentelemetry:opentelemetry-api-incubator:1.63.0-alpha")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }

  test {
    jvmArgs("-Dotel.instrumentation.opentelemetry-api-incubator-1.63.enabled=false")
  }
}
