plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2.6.0,3)")
    // these versions were released as ".bundle" instead of ".jar"
    skip("2.7.5", "2.7.8")
    assertInverse.set(true)
  }
  fail {
    group.set("com.couchbase.client")
    module.set("couchbase-client")
    versions.set("(,)")
  }
}

dependencies {
  implementation(project(":instrumentation:couchbase:couchbase-2-common:javaagent"))

  library("com.couchbase.client:java-client:2.6.0")

  testInstrumentation(project(":instrumentation:couchbase:couchbase-2.0:javaagent"))
  testImplementation(project(":instrumentation:couchbase:couchbase-common:testing"))

  testLibrary("org.springframework.data:spring-data-couchbase:3.1.0.RELEASE")
  testLibrary("com.couchbase.client:encryption:1.0.0")

  latestDepTestLibrary("org.springframework.data:spring-data-couchbase:3.1.+") // see couchbase-3.1 module
  latestDepTestLibrary("com.couchbase.client:java-client:2.+") // see couchbase-3.1 module
}

testing {
  suites {
    val testStableSemconv by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.opt-in=database")
            systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
          }
        }
      }
    }

    val testExperimental by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.instrumentation.couchbase.experimental-span-attributes=true")
            systemProperty("metadataConfig", "otel.instrumentation.couchbase.experimental-span-attributes=true")
          }
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  check {
    dependsOn(testing.suites.named("testStableSemconv"))
    dependsOn(testing.suites.named("testExperimental"))
  }
}
