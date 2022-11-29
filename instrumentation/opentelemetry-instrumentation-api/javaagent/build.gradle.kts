plugins {
  id("otel.javaagent-instrumentation")
}

// note that muzzle is not run against the current SNAPSHOT instrumentation-api, but this is ok
// because the tests are run against the current SNAPSHOT instrumentation-api which will catch any
// muzzle issues in SNAPSHOT instrumentation-api

muzzle {
  pass {
    group.set("io.opentelemetry.instrumentation")
    module.set("opentelemetry-instrumentation-api")
    // currently all 1.0.0+ versions are alpha so they are all skipped
    // if you want to test them anyways, comment out "alpha" from the exclusions in AcceptableVersions.kt
    versions.set("[1.14.0-alpha,)")
    assertInverse.set(true)
    excludeInstrumentationName("opentelemetry-api")
  }
}

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
  if (name.startsWith("muzzle-Assert")) {
    // some names also start with "muzzle-AssertFail", which is conveniently the same length
    val ver = name.substring("muzzle-AssertPass-io.opentelemetry.instrumentation-opentelemetry-instrumentation-api-".length)
    resolutionStrategy {
      dependencySubstitution {
        substitute(module("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api"))
          .using(module("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api:$ver"))
      }
    }
  }
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
