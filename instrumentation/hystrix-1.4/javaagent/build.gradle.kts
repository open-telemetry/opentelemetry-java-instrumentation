import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.netflix.hystrix")
    module.set("hystrix-core")
    versions.set("[1.4.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:rxjava:rxjava-1.0:library"))

  library("com.netflix.hystrix:hystrix-core:1.4.0")
  library("io.reactivex:rxjava:1.0.8")
}

tasks {
  withType<Test>().configureEach {
    // Disable so failure testing below doesn't inadvertently change the behavior.
    jvmArgs("-Dhystrix.command.default.circuitBreaker.enabled=false")

    if (name != "testDisabled") {
      jvmArgs("-Dotel.instrumentation.hystrix.enabled=true")
    }

    // Uncomment for debugging:
    // jvmArgs("-Dhystrix.command.default.execution.timeout.enabled=false")
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }

  testing {
    suites {
      named<JvmTestSuite>("test") {
        executions {
          register("Experimental") {
            jvmArgs("-Dotel.instrumentation.hystrix.experimental-span-attributes=true")
            systemProperty("metadataConfig", "otel.instrumentation.hystrix.experimental-span-attributes=true")
          }
          register("Disabled") {
            filter {
              includeTestsMatching("*DefaultEnablementTest")
            }
          }
        }
      }
    }
  }
}
