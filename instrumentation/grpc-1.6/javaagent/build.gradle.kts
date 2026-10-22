import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.grpc")
    module.set("grpc-core")
    versions.set("[1.6.0,)")
    assertInverse.set(true)
  }
}

val grpcVersion = "1.6.0"

dependencies {
  implementation(project(":instrumentation:grpc-1.6:library"))

  library("io.grpc:grpc-core:$grpcVersion")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testLibrary("io.grpc:grpc-netty:$grpcVersion")
  testLibrary("io.grpc:grpc-protobuf:$grpcVersion")
  testLibrary("io.grpc:grpc-services:$grpcVersion")
  testLibrary("io.grpc:grpc-stub:$grpcVersion")

  testImplementation(project(":instrumentation:grpc-1.6:testing"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          // exclude our grpc library instrumentation, the ContextStorageOverride contained within it
          // breaks the tests
          classpath = classpath.filter {
            !it.absolutePath.contains("opentelemetry-grpc-1.6")
          }
        }
        register("Experimental") {
          telemetryConfig("otel.instrumentation.grpc.experimental-span-attributes", "true")
        }
        register("PreviewSemconv") {
          telemetryConfig("otel.semconv-stability.preview", "rpc")
        }
        register("BothSemconv") {
          telemetryConfig("otel.semconv-stability.preview", "rpc/dup")
        }
        register("ExceptionSignalLogs") {
          telemetryConfig("otel.semconv.exception.signal.preview", "logs")
        }
      }
    }
    register<JvmTestSuite>("testLibraryInstrumentation") {
      dependencies {
        val version = baseVersion(grpcVersion).orLatest()
        implementation(project(":instrumentation:grpc-1.6:library"))
        implementation(project(":instrumentation:grpc-1.6:testing"))
        implementation("io.grpc:grpc-netty:$version")
        implementation("io.grpc:grpc-protobuf:$version")
        implementation("io.grpc:grpc-services:$version")
        implementation("io.grpc:grpc-stub:$version")
      }
      executions {
        configureEach {
          filter {
            includeTestsMatching("*GrpcLibraryInstrumentationTest.unknownService")
            includeTestsMatching("*GrpcLibraryInstrumentationTest.successBlockingStub")
            includeTestsMatching("*GrpcLibraryInstrumentationTest.errorReturned")
          }
        }
        register("PreviewSemconv") {
          jvmArgs("-Dotel.semconv-stability.preview=rpc")
        }
        register("BothSemconv") {
          jvmArgs("-Dotel.semconv-stability.preview=rpc/dup")
        }
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", otelProps.testLatestDeps)
  // The agent context debug mechanism isn't compatible with the bridge approach which may add a
  // gRPC context to the root.
  jvmArgs("-Dotel.javaagent.experimental.thread-propagation-debugger.enabled=false")
  jvmArgs("-Dotel.instrumentation.grpc.client.request-metadata.included=some-*-key")
  jvmArgs("-Dotel.instrumentation.grpc.client.request-metadata.excluded=*-excluded-key")
  jvmArgs("-Dotel.instrumentation.grpc.server.request-metadata.included=some-*-key")
  jvmArgs("-Dotel.instrumentation.grpc.server.request-metadata.excluded=*-excluded-key")
  // latest dep test occasionally fails because network type is ipv6 instead of the expected ipv4
  // and peer address is 0:0:0:0:0:0:0:1 instead of 127.0.0.1
  jvmArgs("-Djava.net.preferIPv4Stack=true")
}

tasks.check {
  dependsOn(testing.suites)
}

if (!otelProps.testLatestDeps) {
  configurations.configureEach {
    if (name.endsWith("RuntimeClasspath")) {
      resolutionStrategy {
        eachDependency {
          // early versions of grpc are not compatible with netty 4.1.101.Final
          if (requested.group == "io.netty") {
            useVersion("4.1.100.Final")
          }
        }
      }
    }
  }

  if (otelProps.denyUnsafe) {
    tasks.withType<Test>().configureEach {
      enabled = false
    }
  }
}
