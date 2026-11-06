plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("org.redisson:redisson:3.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

testing.suites {
  register<JvmTestSuite>("unitTests") {
    dependencies {
      implementation(project())
      implementation(project(":instrumentation-api-incubator"))
      implementation("org.redisson:redisson:3.0.0")
    }
  }

  listOf("3.7.1", "3.7.2", "3.17.0").forEach { version ->
    register<JvmTestSuite>("redisson${version.replace(".", "")}unitTests") {
      sources {
        java {
          setSrcDirs(listOf("src/unitTests/java"))
        }
      }
      dependencies {
        implementation(project())
        implementation(project(":instrumentation-api-incubator"))
        implementation("org.redisson:redisson:$version")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
