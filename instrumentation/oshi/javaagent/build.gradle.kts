plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.github.oshi")
    module.set("oshi-core")
    versions.set("[5.3.1,)")
    // Could not parse POM https://repo.maven.apache.org/maven2/com/github/oshi/oshi-core/6.1.1/oshi-core-6.1.1.pom
    skip("6.1.1")
  }
}

dependencies {
  implementation(project(":instrumentation:oshi:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  library("com.github.oshi:oshi-core:5.3.1")

  testImplementation(project(":instrumentation:oshi:testing"))
}

testing {
  suites {
    val testExperimental by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.instrumentation.oshi.experimental-metrics.enabled=true")
            systemProperty("testExperimental", "true")
            systemProperty("metadataConfig", "otel.instrumentation.oshi.experimental-metrics.enabled=true")
          }
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  check {
    dependsOn(testing.suites.named("testExperimental"))
  }
}
