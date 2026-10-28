import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  compileOnly(project(":muzzle")) // For @NoMuzzle
  compileOnly("org.apache.thrift:libthrift:0.21.0")
  testImplementation("org.apache.thrift:libthrift:0.13.0")
  testImplementation(project(":instrumentation:thrift-0.13:testing"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          systemProperty("testLatestDeps", otelProps.testLatestDeps)
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
