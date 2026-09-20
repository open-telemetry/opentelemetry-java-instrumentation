plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-redis-client")
    versions.set("[4.0.0,)")
    assertInverse.set(true)

    excludeInstrumentationName("vertx-redis-client-4.4.5")
  }
  pass {
    // instrumentation-docs:ignore - verification only, the directive above is the range we document
    name.set("Vert.x Redis 4.4.5 target instrumentation")
    group.set("io.vertx")
    module.set("vertx-redis-client")
    versions.set("[4.4.5,)")
    assertInverse.set(true)

    excludeInstrumentationName("vertx-redis-client-4.0")
  }
}

dependencies {
  library("io.vertx:vertx-redis-client:4.0.0")
  compileOnly("io.vertx:vertx-redis-client:4.4.5") // For RedisConnectOptions added in 4.4.5
  compileOnly("io.vertx:vertx-codegen:4.4.5")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testLibrary("io.vertx:vertx-codegen:4.0.0")
}

testing {
  suites {
    withType<JvmTestSuite>().configureEach {
      if (!name.endsWith("unitTests", true)) {
        sources {
          java {
            srcDir("src/testShared/java")
          }
        }
      }
    }

    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation(project(":instrumentation-api-incubator"))
        implementation("io.vertx:vertx-redis-client:4.4.5")
      }
    }

    register<JvmTestSuite>("stableSemconvUnitTests") {
      sources {
        java {
          srcDir("src/unitTests/java")
        }
      }

      dependencies {
        implementation(project())
        implementation(project(":instrumentation-api-incubator"))
        implementation("io.vertx:vertx-redis-client:4.4.5")
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.opt-in=database,service.peer")
          }
        }
      }
    }

    register<JvmTestSuite>("test403") {
      sources {
        java {
          srcDir("src/testCompatibility/java")
        }
      }

      dependencies {
        implementation("io.vertx:vertx-redis-client:4.0.3")
        implementation("io.vertx:vertx-codegen:4.0.3")
        implementation("org.testcontainers:testcontainers")
      }
    }
    register<JvmTestSuite>("test445") {
      sources {
        java {
          srcDir("src/testCompatibility/java")
        }
      }

      dependencies {
        implementation("io.vertx:vertx-redis-client:4.4.5")
        implementation("io.vertx:vertx-codegen:4.4.5")
        implementation("org.testcontainers:testcontainers")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val stableSemconvSuites = testing.suites.withType(JvmTestSuite::class)
    .filter { !it.name.endsWith("unitTests", true) }
    .associate { suite ->
      suite.name to register<Test>("${suite.name}StableSemconv") {
        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath

        jvmArgs("-Dotel.semconv-stability.opt-in=database,service.peer")
        systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database,service.peer")
      }
    }

  check {
    dependsOn(testing.suites.named("unitTests"), testing.suites.named("stableSemconvUnitTests"))
    if (otelProps.testLatestDeps) {
      dependsOn(stableSemconvSuites.getValue("test"))
    } else {
      dependsOn(testing.suites, stableSemconvSuites.values)
    }
  }
}
