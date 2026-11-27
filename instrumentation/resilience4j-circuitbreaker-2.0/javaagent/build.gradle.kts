import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.github.resilience4j")
    module.set("resilience4j-circuitbreaker")
    versions.set("[2.0.0,)")
    assertInverse.set(true)
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  library("io.github.resilience4j:resilience4j-circuitbreaker:2.0.0")
  latestDepTestLibrary("io.github.resilience4j:resilience4j-circuitbreaker:latest.release")
}

testing.suites {
  executions("test") {
    configureDefault {
      systemProperty("otel.instrumentation.resilience4j-circuitbreaker.enabled", "true")
    }

    register("Disabled") {
      filter {
        includeTestsMatching("*DefaultEnablementTest")
      }
    }

    register("Experimental") {
      systemProperty("otel.instrumentation.resilience4j-circuitbreaker.enabled", "true")
      telemetryConfig("otel.instrumentation.resilience4j-circuitbreaker.experimental-span-attributes", "true")
    }
  }
}
