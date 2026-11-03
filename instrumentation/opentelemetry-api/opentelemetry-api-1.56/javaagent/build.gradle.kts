plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "v1_56"))
  compileOnly("io.opentelemetry:opentelemetry-api-incubator")

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.27:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.40:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.42:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.47:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.50:javaagent"))

  testImplementation("io.opentelemetry:opentelemetry-api-incubator")
}

configurations.configureEach {
  if (name == "testRuntimeClasspath" || name == "testCompileClasspath") {
    resolutionStrategy {
      force("io.opentelemetry:opentelemetry-api:1.56.0")
      force("io.opentelemetry:opentelemetry-api-incubator:1.56.0-alpha")
    }
  }
}

testing {
  suites {
    register<JvmTestSuite>("incubatorTest") {
      dependencies {
        implementation("io.opentelemetry:opentelemetry-api-incubator:1.67.0-alpha")
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.config.file=$projectDir/src/incubatorTest/resources/declarative-config.yaml")
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

  test {
    jvmArgs("-Dotel.config.file=$projectDir/src/test/resources/declarative-config.yaml")
  }
}
