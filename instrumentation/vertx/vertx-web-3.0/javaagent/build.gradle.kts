plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-web")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("io.vertx:vertx-web:3.0.0")

  // We need both versions as different versions of Vert.x use different versions of Netty
  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
}

testing {
  suites {
    register<JvmTestSuite>("version3Test") {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-web-3.0:testing"))

        val version = baseVersion("3.0.0").orLatest("3.+")
        implementation("io.vertx:vertx-web:$version")
        implementation("io.vertx:vertx-jdbc-client:$version")
        implementation("io.vertx:vertx-codegen:$version")
        implementation("io.vertx:vertx-docgen:$version")
      }
    }

    register<JvmTestSuite>("version41Test") {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-web-3.0:testing"))

        val version = baseVersion("4.1.0").orLatest("4.+")
        implementation("io.vertx:vertx-web:$version")
        implementation("io.vertx:vertx-jdbc-client:$version")
        implementation("io.vertx:vertx-codegen:$version")
      }
    }

    register<JvmTestSuite>("version5Test") {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-web-3.0:testing"))

        val version = baseVersion("5.0.0").orLatest()
        implementation("io.vertx:vertx-web:$version")
        implementation("io.vertx:vertx-jdbc-client:$version")
        implementation("io.vertx:vertx-codegen:$version")
      }
    }
  }
}

tasks {
  named("compileVersion5TestJava", JavaCompile::class).configure {
    options.release.set(11)
  }
  val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()
  if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_11)) {
    named("version5Test", Test::class).configure {
      enabled = false
    }
  }

  listOf("version3Test", "version41Test", "version5Test").forEach { suiteName ->
    val suiteProvider = testing.suites.named<JvmTestSuite>(suiteName)
    val testClassName =
      when (suiteName) {
        "version41Test" -> "*Vertx41HttpServerTest"
        "version5Test" -> "*Vertx5HttpServerTest"
        else -> "*VertxHttpServerTest"
      }

    register<Test>("${suiteName}ExceptionSignalLogs") {
      val suite = suiteProvider.get()
      testClassesDirs = suite.sources.output.classesDirs
      classpath = suite.sources.runtimeClasspath
      filter {
        includeTestsMatching("$testClassName.requestWithException")
      }
      jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
      if (suiteName == "version5Test" && !testJavaVersion.isCompatibleWith(JavaVersion.VERSION_11)) {
        enabled = false
      }
    }

    register<Test>("${suiteName}ExceptionSignalLogsDup") {
      val suite = suiteProvider.get()
      testClassesDirs = suite.sources.output.classesDirs
      classpath = suite.sources.runtimeClasspath
      filter {
        includeTestsMatching("$testClassName.requestWithException")
      }
      jvmArgs("-Dotel.semconv.exception.signal.preview=logs/dup")
      if (suiteName == "version5Test" && !testJavaVersion.isCompatibleWith(JavaVersion.VERSION_11)) {
        enabled = false
      }
    }
  }

  check {
    dependsOn(testing.suites)
    dependsOn(
      "version3TestExceptionSignalLogs",
      "version3TestExceptionSignalLogsDup",
      "version41TestExceptionSignalLogs",
      "version41TestExceptionSignalLogsDup",
      "version5TestExceptionSignalLogs",
      "version5TestExceptionSignalLogsDup"
    )
  }
}
