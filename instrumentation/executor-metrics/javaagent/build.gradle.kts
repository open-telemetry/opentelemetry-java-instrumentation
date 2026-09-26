plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:executor-metrics:bootstrap"))

  testImplementation(project(":instrumentation:executor-metrics:testing"))
  testCompileOnly(project(":instrumentation:executor-metrics:bootstrap"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  test {
    jvmArgs("-Dotel.instrumentation.executor-metrics.enabled=true")
    jvmArgs("-Dotel.instrumentation.executors.enabled=false")
    systemProperty("metadataConfig", "otel.instrumentation.executor-metrics.enabled=true")
  }

  val testAllThreadNameNormalization = register<Test>("testAllThreadNameNormalization") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("ThreadPoolExecutorMetricsTest.normalizesExecutorThreadName")
    }
    jvmArgs("-Dotel.instrumentation.executor-metrics.enabled=true")
    jvmArgs("-Dotel.instrumentation.executors.enabled=false")
    jvmArgs("-Dotel.instrumentation.executor-metrics.experimental.name-normalization=all")
    systemProperty("test.name-normalization.expected", "all")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.executor-metrics.enabled=true,otel.instrumentation.executor-metrics.experimental.name-normalization=all",
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
