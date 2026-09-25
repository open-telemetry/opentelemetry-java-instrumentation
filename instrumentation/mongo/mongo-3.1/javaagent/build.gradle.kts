plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    name.set("Mongo 3.1 instrumentation")
    group.set("org.mongodb")
    module.set("mongo-java-driver")
    versions.set("[3.1,)")
    assertInverse.set(true)
    excludeInstrumentationName("mongo-3.7-core")
  }
  pass {
    name.set("Mongo 3.7 instrumentation")
    group.set("org.mongodb")
    module.set("mongo-java-driver")
    versions.set("[3.7, 4.0)")
    assertInverse.set(true)
    excludeInstrumentationName("mongo-3.1-core")
  }
  pass {
    group.set("org.mongodb")
    module.set("mongodb-driver-core")
    versions.set("[3.7, 4.0)")
    assertInverse.set(true)
    excludeInstrumentationName("mongo-3.1-core")
  }
}

dependencies {
  implementation(project(":instrumentation:mongo:mongo-3.1:library"))

  library("org.mongodb:mongo-java-driver:3.1.0")
  compileOnly("org.mongodb:mongo-java-driver:3.7.0")

  testImplementation(project(":instrumentation:mongo:mongo-3.1:testing"))

  testInstrumentation(project(":instrumentation:mongo:mongo-async-3.3:javaagent"))
  testInstrumentation(project(":instrumentation:mongo:mongo-4.0:javaagent"))
}

testing {
  suites {
    register<JvmTestSuite>("library37Test") {
      dependencies {
        implementation("org.mongodb:mongo-java-driver:${baseVersion("3.7.0").orLatest("3.+")}")
        implementation(project(":instrumentation:mongo:mongo-3.1:testing"))
        implementation("com.github.jnr:jnr-unixsocket:0.18")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  val stableSemconvSuites =
    testing.suites.withType(JvmTestSuite::class).map { suite ->
      register<Test>("${suite.name}StableSemconv") {
        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath
        jvmArgs("-Dotel.semconv-stability.opt-in=database")
        systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
      }
    }

  check {
    dependsOn(testing.suites, stableSemconvSuites)
  }
}
