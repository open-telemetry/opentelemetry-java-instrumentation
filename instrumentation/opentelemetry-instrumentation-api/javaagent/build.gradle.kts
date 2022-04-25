plugins {
  id("otel.javaagent-instrumentation")
}

// TODO (trask) muzzle version check doesn't work for two reasons
//  * all of the 1.0.0+ opentelemetry-instrumentation-api releases are alpha so they are skipped
//  * I think dependency substitution is applied to the dependency in the muzzle directive,
//    causing this error:
//      > Could not find io.opentelemetry:opentelemetry-api:.
//      Required by:
//      project :instrumentation:opentelemetry-instrumentation-api:javaagent > project :instrumentation-api

// muzzle {
//   pass {
//     group.set("io.opentelemetry.instrumentation")
//     module.set("opentelemetry-instrumentation-api")
//     versions.set("[1.13.0,)")
//     assertInverse.set(true)
//   }
// }

dependencies {
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))

  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))
  compileOnly(project(":opentelemetry-instrumentation-api-shaded-for-instrumenting", configuration = "shadow"))

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
