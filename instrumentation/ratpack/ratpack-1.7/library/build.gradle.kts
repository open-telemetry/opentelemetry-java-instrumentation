import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.ratpack:ratpack-core:1.7.0")

  testImplementation(project(":instrumentation:ratpack:ratpack-1.4:testing"))

  testLibrary("io.ratpack:ratpack-test:1.7.0")
  testLibrary("io.ratpack:ratpack-guice:1.7.0")

  if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_11)) {
    testImplementation("com.sun.activation:jakarta.activation:1.2.2")
  }
}

testing.suites.named<JvmTestSuite>("test") {
  executions {
    listOf("logs", "logs/dup").forEach { signal ->
      register(if (signal == "logs") "ExceptionSignalLogs" else "ExceptionSignalLogsDup") {
        filter {
          includeTestsMatching("*HttpServerTest.requestWithException")
        }
        telemetryConfig("otel.semconv.exception.signal.preview", signal)
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }

  val testPreviewSemconv = register<Test>("testPreviewSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=service.peer")
  }

  check {
    dependsOn(testPreviewSemconv)
  }
}
