plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  library("org.apache.dubbo:dubbo:2.7.0")

  testImplementation(project(":instrumentation:apache-dubbo-2.7:testing"))

  testLibrary("org.apache.dubbo:dubbo-config-api:2.7.0")
}

testing {
  suites {
    register<JvmTestSuite>("testClusterInvoker") {
      dependencies {
        implementation(project())
        implementation("org.apache.dubbo:dubbo:${baseVersion("2.7.14").orLatest()}")
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
}

tasks {
  val testSuites = testing.suites.withType(JvmTestSuite::class)

  val stableSemconvSuites = testSuites.map { suite ->
    register<Test>("${suite.name}StableSemconv") {
      testClassesDirs = suite.sources.output.classesDirs
      classpath = suite.sources.runtimeClasspath

      jvmArgs("-Dotel.semconv-stability.opt-in=rpc")
      systemProperty("metadataConfig", "otel.semconv-stability.opt-in=rpc")
    }
  }

  val bothSemconvSuites = testSuites.map { suite ->
    register<Test>("${suite.name}BothSemconv") {
      testClassesDirs = suite.sources.output.classesDirs
      classpath = suite.sources.runtimeClasspath

      jvmArgs("-Dotel.semconv-stability.opt-in=rpc/dup")
      systemProperty("metadataConfig", "otel.semconv-stability.opt-in=rpc/dup")
    }
  }

  check {
    dependsOn(testing.suites, stableSemconvSuites, bothSemconvSuites)
  }
}
