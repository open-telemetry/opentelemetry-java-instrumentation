plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.commons")
    module.set("commons-dbcp2")
    versions.set("[2,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.commons:commons-dbcp2:2.0")

  implementation(project(":instrumentation:apache-dbcp-2.0:library"))
  implementation(project(":instrumentation:jdbc:javaagent-common"))

  bootstrap(project(":instrumentation:apache-commons-pool-2.0:bootstrap"))
  bootstrap(project(":instrumentation:jdbc:bootstrap"))

  testImplementation(project(":instrumentation:apache-dbcp-2.0:testing"))
  testInstrumentation(project(":instrumentation:apache-commons-pool-2.0:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testWithCommonsPoolInstrumentation =
    register<Test>("testWithCommonsPoolInstrumentation") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter {
        includeTestsMatching(
          "ApacheDbcpInstrumentationTest.shouldNotReportCommonsPoolMetrics",
        )
      }

      jvmArgs("-Dotel.instrumentation.apache-commons-pool.enabled=true")
    }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testWithCommonsPoolInstrumentation, testStableSemconv)
  }
}
