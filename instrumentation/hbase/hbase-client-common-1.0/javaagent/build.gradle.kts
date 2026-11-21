import io.opentelemetry.instrumentation.gradle.unitTests

plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("org.apache.hbase:hbase-client:1.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

testing.suites {
  unitTests {
    dependencies {
      implementation("org.apache.hbase:hbase-client:1.0.0")
    }
  }

  register<JvmTestSuite>("hbase14UnitTests") {
    sources {
      java {
        setSrcDirs(listOf("src/hbase14UnitTests/java", "src/callTimeoutTest/java"))
      }
      resources.srcDir("src/unitTests/resources")
    }
    dependencies {
      implementation(project())
      implementation("org.apache.hbase:hbase-client:${baseVersion("1.4.0").orLatest("1.+")}")
    }
  }

  register<JvmTestSuite>("hbase20UnitTests") {
    sources {
      java {
        setSrcDirs(listOf("src/hbase20UnitTests/java", "src/callTimeoutTest/java"))
      }
    }
    dependencies {
      implementation(project())
      implementation("org.apache.hbase:hbase-client:${baseVersion("2.0.0").orLatest("2.2.+")}")
    }
  }

  register<JvmTestSuite>("hbase24UnitTests") {
    sources {
      java {
        setSrcDirs(listOf("src/hbase24UnitTests/java", "src/callTimeoutTest/java"))
      }
    }
    dependencies {
      implementation(project())
      implementation("org.apache.hbase:hbase-client:${baseVersion("2.4.18").orLatest("2.4.+")}")
    }
  }
}

tasks.check {
  dependsOn(testing.suites)
}
