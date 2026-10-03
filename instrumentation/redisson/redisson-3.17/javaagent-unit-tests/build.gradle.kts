plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:redisson:redisson-3.17:javaagent"))
  // Redisson 3.24.3 routes configuration through ServiceManager.
  testImplementation("org.redisson:redisson:3.24.3")
}

testing {
  suites {
    register<JvmTestSuite>("redisson47Test") {
      dependencies {
        implementation(project(":instrumentation:redisson:redisson-3.17:javaagent"))
        implementation("org.redisson:redisson:4.7.0")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
