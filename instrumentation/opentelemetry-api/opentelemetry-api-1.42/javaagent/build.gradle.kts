plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "v1_42"))
  compileOnly("io.opentelemetry:opentelemetry-api-incubator")

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.27:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.40:javaagent"))
}

configurations.configureEach {
  if (name.endsWith("testRuntimeClasspath", true) || name.endsWith("testCompileClasspath", true)) {
    resolutionStrategy {
      force("io.opentelemetry:opentelemetry-api:1.42.0")
      force("io.opentelemetry:opentelemetry-api-incubator:1.42.0-alpha")
    }
  }
  if (name == "testRuntimeClasspath") {
    exclude(group = "io.opentelemetry", module = "opentelemetry-api-incubator")
  }
  resolutionStrategy {
    // use older version of opentelemetry-sdk-testing that does not depend on opentelemetry-api-incubator
    force("io.opentelemetry:opentelemetry-sdk-testing:1.47.0")
  }
}

testing {
  suites {
    val incubatorTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation("io.opentelemetry:opentelemetry-api-incubator:1.42.0-alpha")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
