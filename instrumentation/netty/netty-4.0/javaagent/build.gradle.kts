import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.netty")
    module.set("netty-codec-http")
    versions.set("[4.0.0.Final,4.1.0.Final)")
    assertInverse.set(true)
  }
  pass {
    group.set("io.netty")
    module.set("netty-all")
    versions.set("[4.0.0.Final,4.1.0.Final)")
    assertInverse.set(true)
    excludeDependency("io.netty:netty-tcnative")
  }
  fail {
    group.set("io.netty")
    module.set("netty")
    versions.set("[,]")
  }
}

dependencies {
  library("io.netty:netty-codec-http:4.0.0.Final")
  implementation(project(":instrumentation:netty:netty-common-4.0:javaagent"))
  implementation(project(":instrumentation:netty:netty-common-4.0:library"))
  implementation(project(":instrumentation:netty:netty-common:library"))

  testInstrumentation(project(":instrumentation:netty:netty-3.8:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  latestDepTestLibrary("io.netty:netty-codec-http:4.0.+") // see netty-4.1 module
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          filter {
            excludeTestsMatching("Netty40ConnectionSpanTest")
            excludeTestsMatching("Netty40ClientSslTest")
          }
        }
        register("WithPeerServiceMapping") {
          telemetryConfig("otel.instrumentation.common.peer-service-mapping", "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service")
        }
        register("PreviewSemconvWithPeerServiceMapping") {
          telemetryConfig("otel.semconv-stability.preview", "service.peer")
          telemetryConfig("otel.instrumentation.common.peer-service-mapping", "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service")
        }
      }
    }
  }
}

fun Test.connectionTelemetry(includeSsl: Boolean = true) {
  filter {
    includeTestsMatching("Netty40ConnectionSpanTest")
    if (includeSsl) {
      includeTestsMatching("Netty40ClientSslTest")
    }
  }
  include("**/Netty40ConnectionSpanTest.*")
  telemetryConfig("otel.instrumentation.netty.connection-telemetry.enabled", "true")
  if (includeSsl) {
    include("**/Netty40ClientSslTest.*")
    telemetryConfig("otel.instrumentation.netty.ssl-telemetry.enabled", "true")
  }
}

tasks {
  val testConnectionSpan = register<Test>("testConnectionSpan") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    connectionTelemetry()
  }

  val mappedConnectionTests = listOf("WithPeerServiceMapping", "PreviewSemconvWithPeerServiceMapping").map { suffix ->
    register<Test>("testConnectionSpan$suffix") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      connectionTelemetry(includeSsl = false)
      if (suffix == "PreviewSemconvWithPeerServiceMapping") {
        telemetryConfig("otel.semconv-stability.preview", "service.peer")
      }
      telemetryConfig("otel.instrumentation.common.peer-service-mapping", "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service")
    }
  }

  check {
    dependsOn(testConnectionSpan, mappedConnectionTests)
  }
}

// We need to force the dependency to the earliest supported version because other libraries declare newer versions.
if (!otelProps.testLatestDeps) {
  configurations.configureEach {
    if (!name.contains("muzzle")) {
      resolutionStrategy {
        eachDependency {
          // specifying a fixed version for all libraries with io.netty' group
          if (requested.group == "io.netty" && requested.name != "netty-bom") {
            useVersion("4.0.0.Final")
          }
        }
      }
    }
  }
}
