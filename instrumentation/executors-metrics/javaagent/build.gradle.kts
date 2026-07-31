plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  testImplementation(project(":instrumentation:executors:testing"))
  testCompileOnly(project(":instrumentation:executors:bootstrap"))
  testCompileOnly(project(":javaagent-bootstrap"))
}

val enablementTest = "ExecutorsMetricsEnablementTest"

tasks {
  withType<Test>().configureEach {
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    jvmArgs("-Djava.awt.headless=true")
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  test {
    jvmArgs("-Dotel.instrumentation.executors-metrics.enabled=true")
    jvmArgs("-Dotel.instrumentation.executors.enabled=false")
    systemProperty("test.metrics.expected", true)
    systemProperty("metadataConfig", "otel.instrumentation.executors-metrics.enabled=true")
  }

  val testDefaultDisabled = register<Test>("testDefaultDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching(enablementTest)
    }
    systemProperty("test.metrics.expected", false)
  }

  val testExecutorsEnabledOnly = register<Test>("testExecutorsEnabledOnly") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching(enablementTest)
    }
    jvmArgs("-Dotel.instrumentation.executors.enabled=true")
    systemProperty("test.metrics.expected", false)
  }

  val testMetricsExplicitlyDisabled = register<Test>("testMetricsExplicitlyDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching(enablementTest)
    }
    jvmArgs("-Dotel.instrumentation.executors.enabled=true")
    jvmArgs("-Dotel.instrumentation.executors-metrics.enabled=false")
    systemProperty("test.metrics.expected", false)
  }

  val testTrailingThreadNameNormalization = register<Test>("testTrailingThreadNameNormalization") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("ThreadPoolExecutorMetricsTest.normalizesExecutorThreadName")
    }
    jvmArgs("-Dotel.instrumentation.executors-metrics.enabled=true")
    jvmArgs("-Dotel.instrumentation.executors.enabled=false")
    jvmArgs(
      "-Dotel.instrumentation.executors-metrics.experimental.name-normalization=trailing"
    )
    systemProperty("test.name-normalization.expected", "trailing")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.executors-metrics.enabled=true,otel.instrumentation.executors-metrics.experimental.name-normalization=trailing",
    )
  }

  val testDeclarativeThreadNameNormalization =
    register<Test>("testDeclarativeThreadNameNormalization") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter {
        includeTestsMatching("ThreadPoolExecutorMetricsTest.normalizesExecutorThreadName")
      }
      jvmArgs(
        "-Dotel.config.file=$projectDir/src/test/resources/declarative-thread-name-normalization.yaml"
      )
      systemProperty("test.name-normalization.expected", "trailing")
    }

  check {
    dependsOn(
      testDefaultDisabled,
      testExecutorsEnabledOnly,
      testMetricsExplicitlyDisabled,
      testTrailingThreadNameNormalization,
      testDeclarativeThreadNameNormalization,
    )
  }
}
