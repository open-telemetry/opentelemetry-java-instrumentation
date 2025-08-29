plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.apache.logging.log4j:log4j-core:2.17.0")
  annotationProcessor("org.apache.logging.log4j:log4j-core:2.17.0")

  implementation(project(":instrumentation:log4j:log4j-context-data:log4j-context-data-2.17:library-autoconfigure"))

  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testLibrary("com.lmax:disruptor:3.3.4")

  if (findProperty("testLatestDeps") as Boolean) {
    testCompileOnly("biz.aQute.bnd:biz.aQute.bnd.annotation:7.0.0")
    testCompileOnly("com.google.errorprone:error_prone_annotations")
  }
}

testing {
  suites {
    val testAsyncLogger by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-DLog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector")
          }
        }
      }
    }

    val testStableSemconv by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.opt-in=code")
          }
        }
      }
    }

    val testBothSemconv by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.opt-in=code/dup")
          }
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }

  check {
    dependsOn(testing.suites)
  }
}
