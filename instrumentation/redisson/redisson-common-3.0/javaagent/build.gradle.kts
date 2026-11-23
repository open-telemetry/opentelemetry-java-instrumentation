import io.opentelemetry.instrumentation.gradle.unitTests

plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("org.redisson:redisson:3.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

testing.suites {
  unitTests {
    dependencies {
      implementation(project(":instrumentation-api-incubator"))
      implementation("org.redisson:redisson:3.0.0")
    }
  }

  listOf("3.7.1", "3.7.2", "3.17.0").forEach { version ->
    unitTests("redisson${version.replace(".", "")}unitTests") {
      sources {
        java {
          setSrcDirs(listOf("src/unitTests/java"))
        }
      }
      dependencies {
        implementation(project(":instrumentation-api-incubator"))
        implementation("org.redisson:redisson:$version")
      }
    }
  }
}
