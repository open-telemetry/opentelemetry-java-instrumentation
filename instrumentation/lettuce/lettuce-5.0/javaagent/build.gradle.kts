plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.lettuce")
    module.set("lettuce-core")
    versions.set("[5.0.0.RELEASE,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.lettuce:lettuce-core:5.0.0.RELEASE")

  implementation(project(":instrumentation:lettuce:lettuce-common:library"))

  testImplementation("io.lettuce:lettuce-core:5.0.0.RELEASE")

  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:lettuce:lettuce-4.0:javaagent"))

  latestDepTestLibrary("io.lettuce:lettuce-core:5.0.+") // see test suite below
}

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation-api-incubator"))
        implementation(project(":javaagent-extension-api"))
        implementation("io.lettuce:lettuce-core:5.0.0.RELEASE")
      }
    }

    register<JvmTestSuite>("testPreviewSemconvUnitTests") {
      sources {
        java {
          setSrcDirs(listOf("src/unitTests/java"))
        }
      }

      dependencies {
        implementation(project())
        implementation(project(":instrumentation-api-incubator"))
        implementation(project(":javaagent-extension-api"))
        implementation("io.lettuce:lettuce-core:5.0.0.RELEASE")
      }

      targets.all {
        testTask.configure {
          jvmArgs("-Dotel.semconv-stability.preview=service.peer")
        }
      }
    }

    register<JvmTestSuite>("lettuce51Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }

      dependencies {
        val lettuceVersion = baseVersion("5.1.0.RELEASE").orLatest("5.+")
        implementation("io.lettuce:lettuce-core:$lettuceVersion")
        implementation("org.testcontainers:testcontainers")
      }
    }

    register<JvmTestSuite>("lettuce60Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }

      dependencies {
        val lettuceVersion = baseVersion("6.0.0.RELEASE").orLatest("6.4.+")
        implementation("io.lettuce:lettuce-core:$lettuceVersion")
        implementation("org.testcontainers:testcontainers")
      }
    }

    register<JvmTestSuite>("lettuce65Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java", "src/testLettuce65/java"))
        }
      }

      dependencies {
        val lettuceVersion = baseVersion("6.5.0.RELEASE").orLatest()
        implementation("io.lettuce:lettuce-core:$lettuceVersion")
        implementation("org.testcontainers:testcontainers")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  if (otelProps.denyUnsafe) {
    // Lettuce 5.1.0 pulls in Reactor 3.2.0, whose RingBuffer accesses sun.misc.Unsafe directly.
    named("lettuce51Test", Test::class) {
      enabled = false
    }
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.lettuce.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.lettuce.experimental-span-attributes=true")
  }

  val testConnectionTelemetryEnabled = register<Test>("testConnectionTelemetryEnabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.lettuce.connection-telemetry.enabled=true")
    systemProperty("metadataConfig", "otel.instrumentation.lettuce.connection-telemetry.enabled=true")
  }

  val previewSemconvSuites = testing.suites.withType(JvmTestSuite::class)
    .filter { !it.name.endsWith("unitTests", true) }
    .map { suite ->
      register<Test>("${suite.name}PreviewSemconv") {
        val sourceTask = named<Test>(suite.name).get()
        setJvmArgs(sourceTask.jvmArgs)
        setSystemProperties(sourceTask.systemProperties)

        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath

        val previewSemconvConfig = "otel.semconv-stability.preview=service.peer"
        jvmArgs("-D$previewSemconvConfig")
        systemProperty(
          "metadataConfig",
          listOfNotNull(sourceTask.systemProperties["metadataConfig"], previewSemconvConfig)
            .joinToString(","),
        )
        isEnabled = sourceTask.enabled
      }
    }

  val testConnectionTelemetryEnabledPreviewSemconv =
    register<Test>("testConnectionTelemetryEnabledPreviewSemconv") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      jvmArgs(
        "-Dotel.instrumentation.lettuce.connection-telemetry.enabled=true",
        "-Dotel.semconv-stability.preview=service.peer"
      )
      systemProperty(
        "metadataConfig",
        "otel.instrumentation.lettuce.connection-telemetry.enabled=true,otel.semconv-stability.preview=service.peer"
      )
    }

  check {
    dependsOn(
      testing.suites,
      testConnectionTelemetryEnabled,
      testConnectionTelemetryEnabledPreviewSemconv,
      previewSemconvSuites,
      testExperimental,
    )
  }
}
