import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.projectreactor.netty")
    module.set("reactor-netty")
    versions.set("[1.0.0,)")
    assertInverse.set(true)
    excludeInstrumentationName("netty")
  }
  pass {
    group.set("io.projectreactor.netty")
    module.set("reactor-netty-http")
    versions.set("[1.0.0,)")
    assertInverse.set(true)
    excludeInstrumentationName("netty")
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:netty:netty-4.1:javaagent"))
  implementation(project(":instrumentation:netty:netty-4.1:library"))
  implementation(project(":instrumentation:netty:netty-common-4.0:library"))
  implementation(project(":instrumentation:netty:netty-common:library"))
  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  library("io.projectreactor.netty:reactor-netty-http:1.0.0")

  testInstrumentation(project(":instrumentation:reactor:reactor-netty:reactor-netty-0.9:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))

  // using 3.4.3 to avoid the "Spec. Rule 1.3" issue in reactor-core during tests
  // https://github.com/reactor/reactor-core/issues/2579
  testLibrary("io.projectreactor:reactor-test:3.4.3")
  testLibrary("io.projectreactor:reactor-core:3.4.3")
  testImplementation(project(":instrumentation-annotations"))
}

tasks {
  test {
    filter {
      excludeTestsMatching("ReactorNettyConnectionSpanTest")
      excludeTestsMatching("ReactorNettyClientSslTest")
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
            includeTestsMatching("ReactorNettyClientSslTest")
          }
          include("**/ReactorNettyConnectionSpanTest.*", "**/ReactorNettyClientSslTest.*")
          telemetryConfig("otel.instrumentation.netty.ssl-telemetry.enabled", "true")
          telemetryConfig("otel.instrumentation.reactor-netty.connection-telemetry.enabled", "true")
        }
        register("PreviewSemconv") {
          telemetryConfig("otel.semconv-stability.preview", "service.peer")
          filter {
            excludeTestsMatching("ReactorNettyConnectionSpanTest")
            excludeTestsMatching("ReactorNettyClientSslTest")
          }
        }
      }
    }
  }
}
