plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  library("org.apache.dubbo:dubbo:2.7.0")

  testImplementation(project(":instrumentation:apache-dubbo-2.7:testing"))
}

testing.suites {
  register<JvmTestSuite>("testClusterInvoker") {
    dependencies {
      implementation(project())
      implementation("org.apache.dubbo:dubbo:${baseVersion("2.7.14").orLatest()}")
    }
  }
  if (!otelProps.testLatestDeps) {
    // Dubbo 2.7.7's zone-aware cluster casts registry invokers to MockClusterInvoker.
    register<JvmTestSuite>("testDubbo277") {
      dependencies {
        implementation(project())
        implementation("org.apache.dubbo:dubbo:2.7.7")
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("--add-opens=java.base/java.math=ALL-UNNAMED")
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
