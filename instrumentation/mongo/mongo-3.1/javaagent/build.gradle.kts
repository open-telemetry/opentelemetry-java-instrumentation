plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    name.set("mongo-3.1")
    group.set("org.mongodb")
    module.set("mongo-java-driver")
    versions.set("[3.1,)")
    assertInverse.set(true)
    excludeInstrumentationName("mongo-3.7-core")
  }
  pass {
    // instrumentation-docs:ignore - verification only, the directive above is the range we document
    name.set("mongo-3.7")
    group.set("org.mongodb")
    module.set("mongo-java-driver")
    versions.set("[3.7,4.0)")
    assertInverse.set(true)
    excludeInstrumentationName("mongo-3.1-core")
  }
  pass {
    name.set("mongo-3.7-core")
    group.set("org.mongodb")
    module.set("mongodb-driver-core")
    versions.set("[3.7,4.0)")
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
  processResources {
    // The newer API emits its own scope, which needs a version resource as well.
    from(named("generateInstrumentationVersionFile")) {
      include("io.opentelemetry.mongo-3.1.properties")
      rename { "io.opentelemetry.mongo-3.7.properties" }
      into("META-INF/io/opentelemetry/instrumentation")
    }
  }

  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  val stableSemconvSuites = testing.suites.withType(JvmTestSuite::class).map { suite ->
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
