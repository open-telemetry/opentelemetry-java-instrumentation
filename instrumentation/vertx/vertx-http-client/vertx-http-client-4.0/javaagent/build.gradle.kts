import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-core")
    versions.set("[4.0.0,5)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.vertx:vertx-core:4.0.0")

  // vertx-codegen dependency is needed for Xlint's annotation checking
  library("io.vertx:vertx-codegen:4.0.0")

  implementation(project(":instrumentation:vertx:vertx-http-client:vertx-http-client-common-3.0:javaagent"))

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-http-client:vertx-http-client-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-http-client:vertx-http-client-5.0:javaagent"))

  latestDepTestLibrary("io.vertx:vertx-core:4.+") // see vertx-http-client-5.0 module
  latestDepTestLibrary("io.vertx:vertx-codegen:4.+") // see vertx-http-client-5.0 module
}

testing {
  suites {
    register<JvmTestSuite>("version45Test") {
      dependencies {
        implementation("io.vertx:vertx-grpc-client:4.5.8")
        implementation("io.vertx:vertx-grpc-server:4.5.8")
        implementation("io.vertx:vertx-web:4.5.8")
      }
    }

    named<JvmTestSuite>("test") {
      executions {
        register("PreviewSemconv") {
          telemetryConfig("otel.semconv-stability.preview", "service.peer")
        }
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
