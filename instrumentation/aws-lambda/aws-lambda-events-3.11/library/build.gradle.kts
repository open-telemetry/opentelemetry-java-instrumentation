import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:aws-lambda:aws-lambda-events-common-2.2:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  library("com.amazonaws:aws-lambda-java-core:1.0.0")
  library("com.amazonaws:aws-lambda-java-events:3.11.0")

  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  testImplementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
  testImplementation("com.amazonaws:aws-lambda-java-serialization:1.1.5")

  testImplementation(project(":instrumentation:aws-lambda:aws-lambda-events-2.2:testing"))
  testImplementation("uk.org.webcompere:system-stubs-jupiter")
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          // required on jdk17
          jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
          jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
          jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
        }
      }
    }

    register<JvmTestSuite>("core12Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }

      dependencies {
        implementation(project())
        implementation(
          "com.amazonaws:aws-lambda-java-core:${baseVersion("1.2.1").orLatest()}"
        )
      }
      executions {
        configureEach {
          // required on jdk17
          jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
          jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
          jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
        }
      }
    }
  }
}

configurations {
  named("core12TestImplementation") {
    extendsFrom(configurations["testImplementation"])
  }
  named("core12TestRuntimeOnly") {
    extendsFrom(configurations["testRuntimeOnly"])
  }
}
