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

    excludeInstrumentationName("couchbase-2.0-network")
    excludeInstrumentationName("couchbase-2.6-network")
  }
  pass {
    // instrumentation-docs:ignore - verification only, the directive above is the range we document
    name.set("Pre-2.6 network instrumentation")
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2,2.6)")
    assertInverse.set(true)

    excludeInstrumentationName("couchbase-2.0-core")
    excludeInstrumentationName("couchbase-2.6-network")
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

    excludeInstrumentationName("couchbase-2.0-core")
    excludeInstrumentationName("couchbase-2.0-network")
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
    register<JvmTestSuite>("library26Test") {
      dependencies {
        implementation(project(":instrumentation:couchbase:couchbase-common:testing"))
        implementation("com.couchbase.client:java-client:" + if (otelProps.testLatestDeps) "2.+" else "2.6.0")
        implementation("org.springframework.data:spring-data-couchbase:" + if (otelProps.testLatestDeps) "3.1.+" else "3.1.0.RELEASE")
        implementation("com.couchbase.client:encryption:" + if (otelProps.testLatestDeps) "+" else "1.0.0")
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
      testClassesDirs = suite.sources.output.classesDirs
      classpath = suite.sources.runtimeClasspath

      jvmArgs("-Dotel.semconv-stability.opt-in=database")
      systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
    }
  }

  val experimentalSuites = testing.suites.withType(JvmTestSuite::class).map { suite ->
    register<Test>("${suite.name}Experimental") {
      testClassesDirs = suite.sources.output.classesDirs
      classpath = suite.sources.runtimeClasspath

      jvmArgs("-Dotel.instrumentation.couchbase.emit-experimental-telemetry=true")
      systemProperty("metadataConfig", "otel.instrumentation.couchbase.emit-experimental-telemetry=true")
    }
  }

  val library26TestLegacyConfig = register<Test>("library26TestLegacyConfig") {
    val suite = testing.suites.named<JvmTestSuite>("library26Test").get()
    testClassesDirs = suite.sources.output.classesDirs
    classpath = suite.sources.runtimeClasspath

    jvmArgs("-Dotel.instrumentation.couchbase.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.couchbase.experimental-span-attributes=true")
  }

  check {
    dependsOn(testing.suites, stableSemconvSuites, experimentalSuites, library26TestLegacyConfig)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
