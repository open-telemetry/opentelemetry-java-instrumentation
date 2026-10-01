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
    excludeInstrumentationName("mongo-3.7")
  }
  pass {
    // instrumentation-docs:ignore - verification only, the directive above is the range we document
    name.set("mongo-3.7")
    group.set("org.mongodb")
    module.set("mongo-java-driver")
    versions.set("[3.7,4.0)")
    assertInverse.set(true)
    excludeInstrumentationName("mongo-3.1")
  }
  pass {
    name.set("mongo-3.1-client-settings")
    group.set("org.mongodb")
    module.set("mongodb-driver-core")
    versions.set("[3.7,4.0)")
    assertInverse.set(true)
    excludeInstrumentationName("mongo-3.1")
  }
}

dependencies {
  implementation(project(":instrumentation:mongo:mongo-3.1:library"))

  library("org.mongodb:mongo-java-driver:3.1.0")
  compileOnly("org.mongodb:mongo-java-driver:3.7.0")
  latestDepTestLibrary("org.mongodb:mongo-java-driver:3.+") // see mongo-4.0 module

  testImplementation(project(":instrumentation:mongo:mongo-3.1:testing"))

  testInstrumentation(project(":instrumentation:mongo:mongo-async-3.3:javaagent"))
  testInstrumentation(project(":instrumentation:mongo:mongo-4.0:javaagent"))
}

testing {
  suites {
    register<JvmTestSuite>("version37Test") {
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
    // To be removed in 3.0
    // The non-preview 3.7 scope needs its own version resource.
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
      val sourceTask = named<Test>(suite.name).get()
      setJvmArgs(sourceTask.jvmArgs)
      setSystemProperties(sourceTask.systemProperties)
      testClassesDirs = suite.sources.output.classesDirs
      classpath = suite.sources.runtimeClasspath
      jvmArgs("-Dotel.semconv-stability.opt-in=database")
      systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
      isEnabled = sourceTask.enabled
    }
  }

  val version37TestV3Preview = register<Test>("version37TestV3Preview") {
    val sourceTask = named<Test>("version37Test").get()
    setJvmArgs(sourceTask.jvmArgs)
    setSystemProperties(sourceTask.systemProperties)
    testClassesDirs = sourceSets["version37Test"].output.classesDirs
    classpath = sourceSets["version37Test"].runtimeClasspath
    filter {
      includeTestsMatching("*MongoClientTest.emitsInstrumentationScope")
    }
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
    isEnabled = sourceTask.enabled
  }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*MongoClientTest.emitsInstrumentationScope")
    }
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  check {
    dependsOn(testing.suites, stableSemconvSuites, testV3Preview, version37TestV3Preview)
  }
}
