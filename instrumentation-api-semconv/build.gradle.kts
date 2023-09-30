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
  implementation("io.opentelemetry:opentelemetry-extension-incubator")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-sdk")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}

testing {
  suites {
    val testStableHttpSemconv by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation(project(":testing-common"))
        implementation("io.opentelemetry:opentelemetry-sdk")
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
      }
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.opt-in=http")
          }
        }
      }
    }
    val testBothHttpSemconv by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation(project(":testing-common"))
        implementation("io.opentelemetry:opentelemetry-sdk")
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
      }
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.opt-in=http/dup")
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

  test {
    jvmArgs("-Dotel.instrumentation.http.prefer-forwarded-url-scheme=true")
  }

  check {
    dependsOn(testing.suites)
  }
}
