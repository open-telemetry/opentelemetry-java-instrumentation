plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.datastax.oss")
    module.set("java-driver-core")
    versions.set("[4.4,)")
    assertInverse.set(true)
  }
  pass {
    group.set("com.datastax.oss")
    module.set("java-driver-core-shaded")
    versions.set("[4.4,)")
    assertInverse.set(true)
  }
  pass {
    group.set("org.apache.cassandra")
    module.set("java-driver-core")
    versions.set("(,)")
  }
}

dependencies {
  implementation(project(":instrumentation:cassandra:cassandra-4.4:library"))

  if (otelProps.testLatestDeps) {
    library("org.apache.cassandra:java-driver-core:4.18.0")
  } else {
    library("com.datastax.oss:java-driver-core:4.4.0")
  }

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("io.projectreactor:reactor-core:3.4.21")
  testImplementation(project(":instrumentation:cassandra:cassandra-4.4:testing"))

  testInstrumentation(project(":instrumentation:cassandra:cassandra-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:cassandra:cassandra-4.0:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("*CassandraTest.responsePeerComesFromTheChannel")
      includeTestsMatching("*CassandraTest.sniNetworkPeerIsTheResponseChannelProxy")
    }
    jvmArgs("-Dotel.semconv-stability.opt-in=database/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database/dup")
  }

  fun registerShadedTest(
    name: String,
    version: String,
    semconvOptIn: String? = null,
    peerSourceOnly: Boolean = false,
  ): org.gradle.api.tasks.TaskProvider<Test> {
    val shadedClasspath =
      configurations.create("${name}RuntimeClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
        extendsFrom(configurations.testRuntimeClasspath.get())
        resolutionStrategy.dependencySubstitution {
          substitute(module("com.datastax.oss:java-driver-core"))
            .using(module("com.datastax.oss:java-driver-core-shaded:$version"))
          substitute(module("org.apache.cassandra:java-driver-core"))
            .using(module("com.datastax.oss:java-driver-core-shaded:$version"))
        }
      }
    return register<Test>(name) {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = files(sourceSets.test.get().output, shadedClasspath)
      if (otelProps.denyUnsafe) {
        systemProperty("com.datastax.oss.driver.shaded.netty.noUnsafe", "true")
      }
      if (peerSourceOnly) {
        filter {
          includeTestsMatching("*CassandraTest.responsePeerComesFromTheChannel")
          includeTestsMatching("*CassandraTest.sniNetworkPeerIsTheResponseChannelProxy")
        }
      }
      if (semconvOptIn != null) {
        jvmArgs("-Dotel.semconv-stability.opt-in=$semconvOptIn")
        systemProperty("metadataConfig", "otel.semconv-stability.opt-in=$semconvOptIn")
      }
    }
  }

  val testShaded = registerShadedTest("testShaded", "4.4.0")
  val testShadedStableSemconv =
    registerShadedTest("testShadedStableSemconv", "4.4.0", semconvOptIn = "database")
  val testShadedBothSemconv =
    registerShadedTest(
      "testShadedBothSemconv",
      "4.4.0",
      semconvOptIn = "database/dup",
      peerSourceOnly = true,
    )
  val testShadedLatest = registerShadedTest("testShadedLatest", "4.17.0")
  val testShadedLatestStableSemconv =
    registerShadedTest("testShadedLatestStableSemconv", "4.17.0", semconvOptIn = "database")
  val testShadedLatestBothSemconv =
    registerShadedTest(
      "testShadedLatestBothSemconv",
      "4.17.0",
      semconvOptIn = "database/dup",
      peerSourceOnly = true,
    )

  check {
    dependsOn(
      testStableSemconv,
      testBothSemconv,
      testShaded,
      testShadedStableSemconv,
      testShadedBothSemconv,
      testShadedLatest,
      testShadedLatestStableSemconv,
      testShadedLatestBothSemconv,
    )
  }
}
