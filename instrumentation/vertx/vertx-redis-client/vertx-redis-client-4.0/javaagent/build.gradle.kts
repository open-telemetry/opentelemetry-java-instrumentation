plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-redis-client")
    versions.set("[4.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.vertx:vertx-redis-client:4.0.0")
  compileOnly("io.vertx:vertx-codegen:4.0.0")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-redis-client:vertx-redis-client-4.4.5:javaagent"))

  testLibrary("io.vertx:vertx-codegen:4.0.0")
}

testing {
  suites {
    register<JvmTestSuite>("test403") {
      dependencies {
        implementation("io.vertx:vertx-redis-client:4.0.3")
        implementation("io.vertx:vertx-codegen:4.0.3")
        implementation("org.testcontainers:testcontainers")
      }
    }
    register<JvmTestSuite>("test445") {
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
    .associate { suite ->
      suite.name to register<Test>("${suite.name}StableSemconv") {
        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath

        jvmArgs("-Dotel.semconv-stability.opt-in=database,service.peer")
        systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database,service.peer")
      }
    }

  val bothSemconvSuites = testing.suites.withType(JvmTestSuite::class)
    .map { suite ->
      register<Test>("${suite.name}BothSemconv") {
        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath
        filter {
          includeTestsMatching("*VertxRedisClientTest.setCommand")
          includeTestsMatching("*VertxRedisClientTest.concurrentClientsKeepDistinctConfiguredTargets")
          includeTestsMatching("*VertxRedisClient403Test.optionsReuseDoesNotChangeClientTarget")
          includeTestsMatching("*VertxRedisClient445Test.optionsReuseDoesNotChangeClientTarget")
        }

        jvmArgs("-Dotel.semconv-stability.opt-in=database/dup,service.peer")
      }
    }

  check {
    if (otelProps.testLatestDeps) {
      dependsOn(stableSemconvSuites.getValue("test"), bothSemconvSuites)
    } else {
      dependsOn(testing.suites, stableSemconvSuites.values, bothSemconvSuites)
    }
  }
}
