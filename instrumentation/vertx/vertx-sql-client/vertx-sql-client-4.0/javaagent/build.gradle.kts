plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-sql-client")
    versions.set("[4.0.0,5)")
    assertInverse.set(true)
  }
}

dependencies {
  val version = "4.0.0"
  library("io.vertx:vertx-sql-client:$version")
  library("io.vertx:vertx-codegen:$version")

  implementation(project(":instrumentation:vertx:vertx-sql-client:vertx-sql-client-common-4.0:javaagent"))

  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-sql-client:vertx-sql-client-5.0:javaagent"))

  testLibrary("io.vertx:vertx-pg-client:$version")
  testImplementation("io.vertx:vertx-jdbc-client:$version")
  testImplementation("io.agroal:agroal-pool:1.9")
  testImplementation("org.hsqldb:hsqldb:2.3.4")

  latestDepTestLibrary("io.vertx:vertx-sql-client:4.+") // see vertx-sql-client-5.0 module
  latestDepTestLibrary("io.vertx:vertx-pg-client:4.+") // see vertx-sql-client-5.0 module
  latestDepTestLibrary("io.vertx:vertx-jdbc-client:4.+") // see vertx-sql-client-5.0 module
  latestDepTestLibrary("io.vertx:vertx-codegen:4.+") // see vertx-sql-client-5.0 module
}

testing {
  suites {
    // pools over a list of servers were added in 4.2, and the module's own tests run against 4.0
    register<JvmTestSuite>("vertx42Test") {
      dependencies {
        implementation("io.vertx:vertx-sql-client:${baseVersion("4.2.0").orLatest("4.+")}")
        implementation("io.vertx:vertx-pg-client:${baseVersion("4.2.0").orLatest("4.+")}")
        implementation("io.vertx:vertx-codegen:${baseVersion("4.2.0").orLatest("4.+")}")
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
    .map { suite ->
      register<Test>("${suite.name}StableSemconv") {
        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath
        jvmArgs("-Dotel.semconv-stability.opt-in=database,service.peer")
        systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database,service.peer")
      }
    }

  check {
    dependsOn(testing.suites, stableSemconvSuites)
  }
}

if (!otelProps.testLatestDeps) {
  // https://bugs.openjdk.org/browse/JDK-8320431
  otelJava {
    maxJavaVersionForTests.set(JavaVersion.VERSION_21)
  }
}
