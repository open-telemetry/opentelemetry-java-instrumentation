plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2,3)")
    // these versions were released as ".bundle" instead of ".jar"
    skip("2.7.5", "2.7.8")
    assertInverse.set(true)

    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.Couchbase20NetworkInstrumentationModule")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.Couchbase26NetworkInstrumentationModule")
  }
  pass {
    // instrumentation-docs:ignore - verification only, the directive above is the range we document
    name.set("Pre-2.6 network instrumentation")
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2,2.6)")
    assertInverse.set(true)

    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.CouchbaseInstrumentationModule")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.Couchbase26NetworkInstrumentationModule")
  }
  pass {
    // instrumentation-docs:ignore - verification only, the first directive is the range we document
    name.set("Couchbase 2.6 network instrumentation")
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2.6.0,3)")
    // these versions were released as ".bundle" instead of ".jar"
    skip("2.7.5", "2.7.8")
    assertInverse.set(true)

    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.CouchbaseInstrumentationModule")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.Couchbase20NetworkInstrumentationModule")
  }
  fail {
    group.set("com.couchbase.client")
    module.set("couchbase-client")
    versions.set("(,)")
  }
}

dependencies {
  implementation(project(":instrumentation:couchbase:couchbase-common-2.0:javaagent"))
  implementation(project(":instrumentation:rxjava:rxjava-1.0:library"))

  library("com.couchbase.client:java-client:2.0.0")
  compileOnly("com.couchbase.client:core-io:1.6.0")

  testImplementation(project(":instrumentation:couchbase:couchbase-common:testing"))

  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.2:javaagent"))

  latestDepTestLibrary("org.springframework.data:spring-data-couchbase:2.+") // see test suite below
  latestDepTestLibrary("com.couchbase.client:java-client:2.5.+") // see test suite below
}

testing {
  suites {
    register<JvmTestSuite>("version26Test") {
      dependencies {
        implementation(project(":instrumentation:couchbase:couchbase-common:testing"))
        implementation("com.couchbase.client:java-client:${baseVersion("2.6.0").orLatest("2.+")}")
        implementation(
          "org.springframework.data:spring-data-couchbase:${baseVersion("3.1.0.RELEASE").orLatest("3.1.+")}"
        )
        implementation("com.couchbase.client:encryption:${baseVersion("1.0.0").orLatest("1.+")}")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val stableSemconvSuites = testing.suites.withType(JvmTestSuite::class).map { suite ->
    register<Test>("${suite.name}StableSemconv") {
      isEnabled = named<Test>(suite.name).get().enabled
      testClassesDirs = suite.sources.output.classesDirs
      classpath = suite.sources.runtimeClasspath

      jvmArgs("-Dotel.semconv-stability.opt-in=database")
      systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
    }
  }

  val experimentalSuites = testing.suites.withType(JvmTestSuite::class).map { suite ->
    register<Test>("${suite.name}Experimental") {
      isEnabled = named<Test>(suite.name).get().enabled
      testClassesDirs = suite.sources.output.classesDirs
      classpath = suite.sources.runtimeClasspath

      jvmArgs("-Dotel.instrumentation.couchbase.emit-experimental-telemetry=true")
      systemProperty("metadataConfig", "otel.instrumentation.couchbase.emit-experimental-telemetry=true")
    }
  }

  val version26TestLegacyConfig = register<Test>("version26TestLegacyConfig") {
    val suite = testing.suites.named<JvmTestSuite>("version26Test").get()
    isEnabled = named<Test>(suite.name).get().enabled
    testClassesDirs = suite.sources.output.classesDirs
    classpath = suite.sources.runtimeClasspath

    jvmArgs("-Dotel.instrumentation.couchbase.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.couchbase.experimental-span-attributes=true")
  }

  check {
    dependsOn(testing.suites, stableSemconvSuites, experimentalSuites, version26TestLegacyConfig)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
