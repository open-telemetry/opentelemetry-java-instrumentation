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
  api("io.opentelemetry:opentelemetry-semconv")
  api(project(":instrumentation-api"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
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

  val testStatementSanitizerConfig by registering(Test::class) {
    filter {
      includeTestsMatching("StatementSanitizationConfigTest")
    }
    include("**/StatementSanitizationConfigTest.*")
    jvmArgs("-Dotel.instrumentation.common.db-statement-sanitizer.enabled=false")
  }

  test {
    filter {
      excludeTestsMatching("StatementSanitizationConfigTest")
    }
  }

  check {
    dependsOn(testStatementSanitizerConfig)
  }
}
