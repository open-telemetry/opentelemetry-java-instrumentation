plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.linecorp.armeria")
    module.set("armeria")
    versions.set("[1.3.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:armeria:armeria-1.3:library"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  library("com.linecorp.armeria:armeria:1.3.0")
  testLibrary("com.linecorp.armeria:armeria-junit5:1.3.0")

  testImplementation(project(":instrumentation:armeria:armeria-1.3:testing"))

  // needed for latest dep tests
  testCompileOnly("com.google.errorprone:error_prone_annotations")
}

testing {
  suites {
    register<JvmTestSuite>("testArmeria19") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java", "src/testArmeria19/java"))
        }
      }
      dependencies {
        implementation("com.linecorp.armeria:armeria:1.9.2")
        implementation("com.linecorp.armeria:armeria-junit5:1.9.2")
        implementation(project(":instrumentation:armeria:armeria-1.3:testing"))
        compileOnly("com.google.errorprone:error_prone_annotations")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  val testExceptionSignalLogs = register<Test>("testExceptionSignalLogs") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*ArmeriaHttpServerTest.requestWithException")
    }
    jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
    systemProperty("metadataConfig", "otel.semconv.exception.signal.preview=logs")
  }

  val testExceptionSignalLogsDup = register<Test>("testExceptionSignalLogsDup") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*ArmeriaHttpServerTest.requestWithException")
    }
    jvmArgs("-Dotel.semconv.exception.signal.preview=logs/dup")
    systemProperty("metadataConfig", "otel.semconv.exception.signal.preview=logs/dup")
  }

  val testArmeria19ExceptionSignalLogs = register<Test>("testArmeria19ExceptionSignalLogs") {
    val suite = testing.suites.named<JvmTestSuite>("testArmeria19").get()
    testClassesDirs = suite.sources.output.classesDirs
    classpath = suite.sources.runtimeClasspath
    filter {
      includeTestsMatching("*ArmeriaHttpServerTest.requestWithException")
    }
    jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
    systemProperty("metadataConfig", "otel.semconv.exception.signal.preview=logs")
  }

  val testArmeria19ExceptionSignalLogsDup =
    register<Test>("testArmeria19ExceptionSignalLogsDup") {
      val suite = testing.suites.named<JvmTestSuite>("testArmeria19").get()
      testClassesDirs = suite.sources.output.classesDirs
      classpath = suite.sources.runtimeClasspath
      filter {
        includeTestsMatching("*ArmeriaHttpServerTest.requestWithException")
      }
      jvmArgs("-Dotel.semconv.exception.signal.preview=logs/dup")
      systemProperty("metadataConfig", "otel.semconv.exception.signal.preview=logs/dup")
    }

  val previewSemconvSuites = testing.suites.withType(JvmTestSuite::class)
    .map { suite ->
      register<Test>("${suite.name}PreviewSemconv") {
        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath

        jvmArgs("-Dotel.semconv-stability.preview=service.peer")
        systemProperty("metadataConfig", "otel.semconv-stability.preview=service.peer")
      }
    }

  check {
    dependsOn(
      testing.suites,
      previewSemconvSuites,
      testExceptionSignalLogs,
      testExceptionSignalLogsDup,
      testArmeria19ExceptionSignalLogs,
      testArmeria19ExceptionSignalLogsDup
    )
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
