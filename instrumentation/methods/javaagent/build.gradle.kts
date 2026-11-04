import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk.set(true)
  }
}

dependencies {
  compileOnly(project(":instrumentation-annotations-support"))
}

testing {
  suites {
    register<JvmTestSuite>("declarativeConfigTest") {
    }

    executions("test") {
      configureDefault {
        jvmArgs(
          "-Dotel.instrumentation.methods.include=io.opentelemetry.javaagent.instrumentation.methods.MethodTest\$ConfigTracedCallable[call];io.opentelemetry.javaagent.instrumentation.methods.MethodTest\$ConfigTracedCompletableFuture[getResult];javax.naming.directory.InitialDirContext[search]"
        )
      }
    }
    executions("declarativeConfigTest") {
      configureEach {
        jvmArgs(
          "-Dotel.config.file=$projectDir/src/declarativeConfigTest/resources/declarative-config.yaml"
        )
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
