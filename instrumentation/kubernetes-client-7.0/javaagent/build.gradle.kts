plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.kubernetes")
    module.set("client-java-api")
    versions.set("[7.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.kubernetes:client-java-api:7.0.0")

  testInstrumentation(project(":instrumentation:okhttp:okhttp-3.0:javaagent"))

  latestDepTestLibrary("io.kubernetes:client-java-api:19.+") // see test suite below
}

testing {
  suites {
    val version20Test by registering(JvmTestSuite::class) {
      dependencies {
        if (findProperty("testLatestDeps") as Boolean) {
          implementation("io.kubernetes:client-java-api:latest.release")
        } else {
          implementation("io.kubernetes:client-java-api:20.0.0")
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

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.kubernetes-client.experimental-span-attributes=true")
  systemProperty("otel.instrumentation.common.peer-service-mapping", "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service")
}
