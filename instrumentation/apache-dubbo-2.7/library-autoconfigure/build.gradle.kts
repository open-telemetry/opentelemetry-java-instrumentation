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
    val testLatestDepDubbo by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:apache-dubbo-2.7:library-autoconfigure"))
        implementation("org.apache.dubbo:dubbo:+")
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
}

if (findProperty("testLatestDeps") as Boolean) {
  tasks.check {
    dependsOn(testing.suites)
  }
}
