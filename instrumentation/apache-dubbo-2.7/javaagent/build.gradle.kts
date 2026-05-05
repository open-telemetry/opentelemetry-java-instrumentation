plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.dubbo")
    module.set("dubbo")
    versions.set("[2.7,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:apache-dubbo-2.7:library-autoconfigure"))

  library("org.apache.dubbo:dubbo:2.7.0")
}

testing {
  suites {
    // using a test suite to ensure that project(":instrumentation:apache-dubbo-2.7:library-autoconfigure")
    // is not available on test runtime class path, otherwise instrumentation from library-autoconfigure
    // module would be used instead of the javaagent instrumentation that we want to test
    val testDubbo by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:apache-dubbo-2.7:testing"))
        val version = baseVersion("2.7.0").orLatest()
        implementation("org.apache.dubbo:dubbo:$version")
        implementation("org.apache.dubbo:dubbo-config-api:$version")
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", otelProps.testLatestDeps)
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  // to suppress non-fatal errors on jdk17
  jvmArgs("--add-opens=java.base/java.math=ALL-UNNAMED")
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")

  systemProperty("collectMetadata", otelProps.collectMetadata)
}

val stableSemconvSuites = testing.suites.withType(JvmTestSuite::class).map { suite ->
  tasks.register<Test>("${suite.name}StableSemconv") {
    testClassesDirs = suite.sources.output.classesDirs
    classpath = suite.sources.runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=rpc,service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=rpc,service.peer")
  }
}

val bothSemconvSuites = testing.suites.withType(JvmTestSuite::class).map { suite ->
  tasks.register<Test>("${suite.name}BothSemconv") {
    testClassesDirs = suite.sources.output.classesDirs
    classpath = suite.sources.runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=rpc/dup,service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=rpc/dup,service.peer")
  }
}

tasks {
  check {
    dependsOn(testing.suites, stableSemconvSuites, bothSemconvSuites)
  }
}
