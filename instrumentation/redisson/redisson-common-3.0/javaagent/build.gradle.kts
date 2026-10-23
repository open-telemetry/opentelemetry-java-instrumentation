plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("org.redisson:redisson:3.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

testing {
  suites {
    listOf("3.0.0", "3.7.1", "3.7.2", "3.17.0").forEach { version ->
      register<JvmTestSuite>("redisson${version.replace(".", "")}unitTests") {
        sources {
          java {
            setSrcDirs(listOf("src/unitTests/java"))
          }
        }
        dependencies {
          implementation(project())
          implementation("org.redisson:redisson:$version")
        }
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
