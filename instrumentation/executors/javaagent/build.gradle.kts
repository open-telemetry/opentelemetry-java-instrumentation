plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
}

muzzle {
  pass {
    coreJdk()
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  testImplementation(project(":instrumentation:executors:testing"))

  testImplementation("org.scala-lang:scala-library:2.11.12")
}

testing {
  suites {
    // CompletableFuture behaves differently if ForkJoinPool has no parallelism
    val testNoParallelism by registering(JvmTestSuite::class) {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }

      dependencies {
        implementation(project(":instrumentation:executors:testing"))
      }

      targets {
        all {
          testTask.configure {
            systemProperty("java.util.concurrent.ForkJoinPool.common.parallelism", 1)
          }
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    jvmArgs(
      "-Dotel.instrumentation.executors.include=io.opentelemetry.javaagent.instrumentation.executors.ExecutorInstrumentationTest\$CustomThreadPoolExecutor,io.opentelemetry.javaagent.instrumentation.executors.ThreadPoolExecutorTest\$RunnableCheckingThreadPoolExecutor"
    )
    jvmArgs("-Djava.awt.headless=true")
  }

  check {
    dependsOn(testing.suites)
  }
}
