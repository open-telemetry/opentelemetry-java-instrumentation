plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))

  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))

  testImplementation(project(":instrumentation-api-semconv"))
  testImplementation(project(":instrumentation:opentelemetry-instrumentation-api:testing"))
  testInstrumentation(project(":instrumentation:opentelemetry-instrumentation-api:testing"))
}

testing {
  suites {
    val testOldServerSpan by registering(JvmTestSuite::class) {
      dependencies {
        implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-semconv")
        implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
        implementation(project(":instrumentation:opentelemetry-instrumentation-api:testing"))
      }
    }
  }
}

configurations.configureEach {
  if (name.startsWith("testOldServerSpan")) {
    resolutionStrategy {
      dependencySubstitution {
        // version 1.13.0 contains the old ServerSpan implementation that uses SERVER_KEY context key
        substitute(module("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-semconv"))
          .using(module("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-semconv:1.13.0-alpha"))
        substitute(module("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api"))
          .using(module("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api:1.13.0-alpha"))
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
