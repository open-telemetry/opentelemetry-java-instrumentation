plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.tomcat")
    module.set("tomcat-jdbc")
    versions.set("[8.5.0,)")
    // no assertInverse because tomcat-jdbc < 8.5 doesn't have methods that we hook into
  }
}

dependencies {
  compileOnly("org.apache.tomcat:tomcat-jdbc:8.5.0")
  testImplementation("org.apache.tomcat:tomcat-jdbc:8.5.0")
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

testing {
  suites {
    val testStableSemconv by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.opt-in=database")
            systemProperty("collectMetadata", collectMetadata)
            systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
          }
        }
      }
    }
  }
}

tasks {
  test {
    systemProperty("collectMetadata", collectMetadata)
  }

  check {
    dependsOn(testing.suites)
  }
}
