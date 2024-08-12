plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "v1_31"))
  compileOnly("io.opentelemetry:opentelemetry-api-incubator")

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.4:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.10:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.15:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.27:javaagent"))

  testImplementation("io.opentelemetry:opentelemetry-extension-incubator:1.31.0-alpha")
}

configurations.configureEach {
  if (name == "testRuntimeClasspath" || name == "testCompileClasspath" || name.startsWith("noopTest")) {
    resolutionStrategy {
      force("io.opentelemetry:opentelemetry-api:1.31.0")
    }
  }
}

testing {
  suites {
    val noopTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation("io.opentelemetry:opentelemetry-extension-incubator:1.31.0-alpha")
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dtesting.exporter.enabled=false")
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
