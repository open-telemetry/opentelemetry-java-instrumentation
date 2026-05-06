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

  // We need both versions as different versions of Vert.x use different versions of Netty
  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
}

testing {
  suites {
    val version3Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-web-3.0:testing"))

        val version = baseVersion("3.0.0").orLatest("3.+")
        implementation("io.vertx:vertx-web:$version")
        implementation("io.vertx:vertx-jdbc-client:$version")
        implementation("io.vertx:vertx-codegen:$version")
        implementation("io.vertx:vertx-docgen:$version")
      }
    }

    val version41Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-web-3.0:testing"))

        val version = baseVersion("4.1.0").orLatest("4.+")
        implementation("io.vertx:vertx-web:$version")
        implementation("io.vertx:vertx-jdbc-client:$version")
        implementation("io.vertx:vertx-codegen:$version")
      }
    }

    val version5Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-web-3.0:testing"))

        val version = baseVersion("5.0.0").orLatest()
        implementation("io.vertx:vertx-web:$version")
        implementation("io.vertx:vertx-jdbc-client:$version")
        implementation("io.vertx:vertx-codegen:$version")
      }
    }
  }
}

tasks {
  named("compileVersion5TestJava", JavaCompile::class).configure {
    options.release.set(11)
  }
  val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()
  if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_11)) {
    named("version5Test", Test::class).configure {
      enabled = false
    }
  }

  check {
    dependsOn(testing.suites)
  }
}
