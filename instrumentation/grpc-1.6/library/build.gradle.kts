import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
  id("otel.animalsniffer-conventions")
}

val grpcVersion = "1.6.0"

dependencies {
  library("io.grpc:grpc-core:$grpcVersion")

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
          systemProperty("testLatestDeps", otelProps.testLatestDeps)
          // latest dep test occasionally fails because network type is ipv6 instead of the expected ipv4
          // and peer address is 0:0:0:0:0:0:0:1 instead of 127.0.0.1
          jvmArgs("-Djava.net.preferIPv4Stack=true")
        }
        register("PreviewSemconv") {
          jvmArgs("-Dotel.semconv-stability.preview=rpc")
        }
        register("BothSemconv") {
          jvmArgs("-Dotel.semconv-stability.preview=rpc/dup")
        }
        register("ExceptionSignalLogs") {
          jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
        }
      }
    }
  }
}

if (!otelProps.testLatestDeps) {
  configurations.testRuntimeClasspath {
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
