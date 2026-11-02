plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("redis.clients")
    module.set("jedis")
    versions.set("[3.0.0,4)")
    skip("jedis-3.6.2")
    assertInverse.set(true)
  }
}

dependencies {
  library("redis.clients:jedis:3.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:jedis:jedis-common:javaagent"))

  testInstrumentation(project(":instrumentation:jedis:jedis-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:jedis:jedis-4.0:javaagent"))

  latestDepTestLibrary("redis.clients:jedis:3.+") // see jedis-4.0 module
}

testing {
  suites {
    register<JvmTestSuite>("jedis36Test") {
      dependencies {
        implementation("redis.clients:jedis:3.6.1")
        implementation("org.testcontainers:testcontainers")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  check {
    dependsOn(testing.suites)
  }
}
