import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.projectreactor.netty")
    module.set("reactor-netty")
    versions.set("[0.8.2.RELEASE,1.0.0)")
    assertInverse.set(true)
    excludeInstrumentationName("netty")
  }
  fail {
    group.set("io.projectreactor.netty")
    module.set("reactor-netty-http")
    versions.set("[1.0.0,)")
    assertInverse.set(true)
    excludeInstrumentationName("netty")
  }
}

dependencies {
  implementation(project(":instrumentation:netty:netty-4.1:javaagent"))
  implementation(project(":instrumentation:netty:netty-4.1:library"))
  library("io.projectreactor.netty:reactor-netty:0.9.0.RELEASE")

  testInstrumentation(project(":instrumentation:reactor:reactor-netty:reactor-netty-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))

  latestDepTestLibrary("io.projectreactor.netty:reactor-netty:0.+") // see reactor-netty-1.0 modules
}

tasks {
  test {
    filter {
      excludeTestsMatching("ReactorNettyConnectionSpanTest")
    }
  }
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("ConnectionSpan") {
          filter {
            includeTestsMatching("ReactorNettyConnectionSpanTest")
          }
          include("**/ReactorNettyConnectionSpanTest.*")
          telemetryConfig("otel.instrumentation.netty.connection-telemetry.enabled", "true")
        }
        register("PreviewSemconv") {
          telemetryConfig("otel.semconv-stability.preview", "service.peer")
          filter {
            excludeTestsMatching("ReactorNettyConnectionSpanTest")
          }
        }
      }
    }
  }
}
