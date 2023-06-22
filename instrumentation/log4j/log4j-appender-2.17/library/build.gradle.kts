plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.apache.logging.log4j:log4j-core:2.17.0")

  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  testAnnotationProcessor("com.google.auto.service:auto-service")
  testCompileOnly("com.google.auto.service:auto-service-annotations")
}

tasks {
  val testUseGlobalOpenTelemetry by registering(Test::class) {
    filter {
      includeTestsMatching("*OpenTelemetryAppenderGlobalTest*")
    }
    include("**/*OpenTelemetryAppenderGlobalTest.*")
    jvmArgs(
      "-Dotel.java.global-autoconfigure.enabled=true",
      "-Dlog4j.configurationFile=" + project.projectDir.toString() + "/src/test/resources/log4j2-use-global.xml",
      "-Dotel.metrics.exporter=none",
      "-Dotel.traces.exporter=none",
      "-Dotel.logs.exporter=none"
    )
  }

  test {
    filter {
      excludeTestsMatching("*OpenTelemetryAppenderGlobalTest")
    }
  }

  check {
    dependsOn(testUseGlobalOpenTelemetry)
  }
}
