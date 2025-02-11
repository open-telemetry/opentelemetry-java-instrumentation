plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "v1_37"))
  compileOnly("io.opentelemetry:opentelemetry-api-incubator")

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.4:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.10:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.15:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.27:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.31:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.32:javaagent"))
}

configurations.configureEach {
  if (name.endsWith("testRuntimeClasspath", true) || name.endsWith("testCompileClasspath", true)) {
    resolutionStrategy {
      force("io.opentelemetry:opentelemetry-api:1.37.0")
      force("io.opentelemetry:opentelemetry-api-incubator:1.37.0-alpha")
    }
    if (name.startsWith("oldAndNewIncubatorTest")) {
      resolutionStrategy {
        force("io.opentelemetry:opentelemetry-api-incubator:1.37.0-alpha")
        force("io.opentelemetry:opentelemetry-extension-incubator:1.32.0-alpha")
      }
    }
    if (name.equals("testRuntimeClasspath")) {
      exclude(group = "io.opentelemetry", module = "opentelemetry-api-incubator")
    }
  }
}

testing {
  suites {
    val incubatorTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation("io.opentelemetry:opentelemetry-api-incubator:1.37.0-alpha")
      }
    }
    val oldAndNewIncubatorTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation("io.opentelemetry:opentelemetry-api-incubator:1.37.0-alpha")
        implementation("io.opentelemetry:opentelemetry-extension-incubator:1.32.0-alpha")
      }
    }
    val noopTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation("io.opentelemetry:opentelemetry-api-incubator:1.37.0-alpha")
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
