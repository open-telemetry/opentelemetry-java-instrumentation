plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk()
  }
}

dependencies {
  compileOnly(project(":javaagent-tooling"))
  compileOnly(project(":instrumentation-annotations-support"))
}

tasks.test {
  jvmArgs(
    "-Dotel.instrumentation.methods.include=io.opentelemetry.javaagent.instrumentation.methods.MethodTest\$ConfigTracedCallable[call];io.opentelemetry.javaagent.instrumentation.methods.MethodTest\$ConfigTracedCompletableFuture[getResult];javax.naming.directory.InitialDirContext[search]"
  )
}

testing {
  suites {
    val declarativeConfigTest by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs(
              "-Dotel.experimental.config.file=$projectDir/src/declarativeConfigTest/resources/declarative-config.yaml"
            )
          }
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
