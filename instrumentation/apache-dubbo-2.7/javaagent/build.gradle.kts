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

val latestDepTest = findProperty("testLatestDeps") as Boolean

testing {
  suites {
    // using a test suite to ensure that project(":instrumentation:apache-dubbo-2.7:library-autoconfigure")
    // is not available on test runtime class path, otherwise instrumentation from library-autoconfigure
    // module would be used instead of the javaagent instrumentation that we want to test
    val testDubbo by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:apache-dubbo-2.7:testing"))
        if (latestDepTest) {
          implementation("org.apache.dubbo:dubbo:latest.release")
          implementation("org.apache.dubbo:dubbo-config-api:latest.release")
        } else {
          implementation("org.apache.dubbo:dubbo:2.7.0")
          implementation("org.apache.dubbo:dubbo-config-api:2.7.0")
        }
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  // to suppress non-fatal errors on jdk17
  jvmArgs("--add-opens=java.base/java.math=ALL-UNNAMED")
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")

  systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  systemProperty("collectSpans", true)
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
