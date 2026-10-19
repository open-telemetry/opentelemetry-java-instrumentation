import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("biz.paluch.redis")
    module.set("lettuce")
    versions.set("[4.0.Final,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("biz.paluch.redis:lettuce:4.0.Final")

  testInstrumentation(project(":instrumentation:lettuce:lettuce-5.0:javaagent"))

  latestDepTestLibrary("biz.paluch.redis:lettuce:4.+") // see lettuce-5.0 module
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("WithPeerServiceMapping") {
          telemetryConfig(
            "otel.instrumentation.common.peer-service-mapping",
            "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service",
          )
        }
        register("PreviewSemconvWithPeerServiceMapping") {
          telemetryConfig("otel.semconv-stability.preview", "service.peer")
          telemetryConfig(
            "otel.instrumentation.common.peer-service-mapping",
            "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service",
          )
        }
        register("ConnectionTelemetryEnabledWithPeerServiceMapping") {
          telemetryConfig("otel.instrumentation.lettuce.connection-telemetry.enabled", "true")
          telemetryConfig(
            "otel.instrumentation.common.peer-service-mapping",
            "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service",
          )
        }
        register("ConnectionTelemetryEnabledPreviewSemconvWithPeerServiceMapping") {
          telemetryConfig("otel.instrumentation.lettuce.connection-telemetry.enabled", "true")
          telemetryConfig("otel.semconv-stability.preview", "service.peer")
          telemetryConfig(
            "otel.instrumentation.common.peer-service-mapping",
            "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service",
          )
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.lettuce.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.lettuce.experimental-span-attributes=true")
  }

  val testConnectionTelemetryEnabled = register<Test>("testConnectionTelemetryEnabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.lettuce.connection-telemetry.enabled=true")
    systemProperty("metadataConfig", "otel.instrumentation.lettuce.connection-telemetry.enabled=true")
  }

  val testPreviewSemconv = register<Test>("testPreviewSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=service.peer")
  }

  val testConnectionTelemetryEnabledPreviewSemconv =
    register<Test>("testConnectionTelemetryEnabledPreviewSemconv") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      jvmArgs(
        "-Dotel.instrumentation.lettuce.connection-telemetry.enabled=true",
        "-Dotel.semconv-stability.preview=service.peer"
      )
      systemProperty(
        "metadataConfig",
        "otel.instrumentation.lettuce.connection-telemetry.enabled=true,otel.semconv-stability.preview=service.peer"
      )
    }

  check {
    dependsOn(
      testConnectionTelemetryEnabled,
      testConnectionTelemetryEnabledPreviewSemconv,
      testPreviewSemconv,
      testExperimental
    )
  }
}
