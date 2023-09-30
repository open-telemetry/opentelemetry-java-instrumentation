plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-web")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("io.vertx:vertx-web:3.0.0")

  // We need both version as different versions of Vert.x use different versions of Netty
  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
}

testing {
  suites {
    val version3Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-web-3.0:testing"))

        implementation("io.vertx:vertx-web:3.0.0")
        implementation("io.vertx:vertx-jdbc-client:3.0.0")
        implementation("io.vertx:vertx-codegen:3.0.0")
        implementation("io.vertx:vertx-docgen:3.0.0")
      }
    }

    val latestDepTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-web-3.0:testing"))

        implementation("io.vertx:vertx-web:+")
        implementation("io.vertx:vertx-jdbc-client:+")
        implementation("io.vertx:vertx-codegen:+")
      }
    }
  }
}

tasks {
  if (findProperty("testLatestDeps") as Boolean) {
    // disable regular test running and compiling tasks when latest dep test task is run
    named("test") {
      enabled = false
    }
    named("compileTestGroovy") {
      enabled = false
    }

    check {
      dependsOn(testing.suites)
    }
  }

  tasks {
    val testStableSemconv by registering(Test::class) {
      jvmArgs("-Dotel.semconv-stability.opt-in=http")
    }

    check {
      dependsOn(testStableSemconv)
    }
  }
}
