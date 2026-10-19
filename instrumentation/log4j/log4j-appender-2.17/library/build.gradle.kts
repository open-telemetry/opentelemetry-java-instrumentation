import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.apache.logging.log4j:log4j-core:2.17.0")
  annotationProcessor("org.apache.logging.log4j:log4j-core:2.17.0")

  testImplementation(project(":instrumentation:log4j:log4j-appender-2.17:testing"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testLibrary("com.lmax:disruptor:3.3.4")

  if (otelProps.testLatestDeps) {
    testCompileOnly("biz.aQute.bnd:biz.aQute.bnd.annotation:7.0.0")
    testCompileOnly("com.google.errorprone:error_prone_annotations")
  }
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          systemProperty("testLatestDeps", otelProps.testLatestDeps)
        }
        register("AsyncLogger") {
          jvmArgs(
            "-DLog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector",
            "-Dlog4j2.ContextDataInjector=io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppenderContextDataInjector",
          )
        }
      }
    }
  }
}
