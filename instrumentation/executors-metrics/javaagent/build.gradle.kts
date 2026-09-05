plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors-metrics:bootstrap"))

  testImplementation(project(":instrumentation:executors-metrics:testing"))
  testCompileOnly(project(":instrumentation:executors-metrics:bootstrap"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  test {
    jvmArgs("-Dotel.instrumentation.executors-metrics.enabled=true")
    jvmArgs("-Dotel.instrumentation.executors.enabled=false")
    systemProperty("metadataConfig", "otel.instrumentation.executors-metrics.enabled=true")
  }

  val testAllThreadNameNormalization = register<Test>("testAllThreadNameNormalization") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("ThreadPoolExecutorMetricsTest.normalizesExecutorThreadName")
    }
    jvmArgs("-Dotel.instrumentation.executors-metrics.enabled=true")
    jvmArgs("-Dotel.instrumentation.executors.enabled=false")
    jvmArgs("-Dotel.instrumentation.executors-metrics.experimental.name-normalization=all")
    systemProperty("test.name-normalization.expected", "all")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.executors-metrics.enabled=true,otel.instrumentation.executors-metrics.experimental.name-normalization=all",
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
      systemProperty("test.name-normalization.expected", "all")
    }

  check {
    dependsOn(
      testAllThreadNameNormalization,
      testDeclarativeThreadNameNormalization,
    )
  }
}
