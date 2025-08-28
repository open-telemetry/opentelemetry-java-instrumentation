plugins {
  id("org.xbib.gradle.plugin.jflex")

  id("otel.java-conventions")
  id("otel.animalsniffer-conventions")
  id("otel.jacoco-conventions")
  id("otel.japicmp-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  api("io.opentelemetry.semconv:opentelemetry-semconv")
  api(project(":instrumentation-api"))
  api("io.opentelemetry:opentelemetry-api-incubator")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-incubator")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-sdk")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.opentelemetry.semconv:opentelemetry-semconv-incubating")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-incubator")
}

testing {
  suites {
    val testStableSemconv by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.opt-in=database,code")
          }
        }
      }
    }

    val testBothSemconv by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.opt-in=database/dup,code/dup")
          }
        }
      }
    }
  }
}

tasks {
  // exclude auto-generated code
  named<Checkstyle>("checkstyleMain") {
    exclude("**/AutoSqlSanitizer.java")
  }

  // Work around https://github.com/jflex-de/jflex/issues/762
  compileJava {
    with(options) {
      compilerArgs.add("-Xlint:-fallthrough")
    }
  }

  sourcesJar {
    dependsOn("generateJflex")
  }

  check {
    dependsOn(testing.suites.named("testStableSemconv"))
    dependsOn(testing.suites.named("testBothSemconv"))
  }
}
