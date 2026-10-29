import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.typesafe.play")
    module.set("play-ahc-ws-standalone_2.11")
    versions.set("[1.0.0,2.0.0)")
    assertInverse.set(true)
  }
  pass {
    group.set("com.typesafe.play")
    module.set("play-ahc-ws-standalone_2.12")
    versions.set("[1.0.0,2.0.0)")
    assertInverse.set(true)
  }
  fail {
    group.set("com.typesafe.play")
    module.set("play-ahc-ws-standalone_2.13")
    versions.set("[,]")
  }
}

val scalaVersion = "2.12"

dependencies {
  library("com.typesafe.play:play-ahc-ws-standalone_$scalaVersion:1.0.2")

  implementation(project(":instrumentation:play:play-ws:play-ws-common-1.0:javaagent"))

  testImplementation(project(":instrumentation:play:play-ws:play-ws-common-1.0:testing"))

  // These are to ensure cross compatibility
  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-http-10.0:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-actor-2.3:javaagent"))

  latestDepTestLibrary("com.typesafe.play:play-ahc-ws-standalone_$scalaVersion:1.+") // see play-ws-2.0 module
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("WithServerServiceMapping") {
          telemetryConfig("otel.instrumentation.common.server-service-mapping", "127.0.0.1=test-server-service,localhost=test-server-service,192.0.2.1=test-server-service")
        }
      }
    }
  }
}

tasks {
  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
