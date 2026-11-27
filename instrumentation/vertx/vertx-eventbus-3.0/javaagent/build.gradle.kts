plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-core")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("io.vertx:vertx-core:3.0.0")
  // vertx-codegen / vertx-docgen are needed for Xlint's annotation checking
  compileOnly("io.vertx:vertx-codegen:3.0.0")
  compileOnly("io.vertx:vertx-docgen:3.0.0")
}

testing {
  suites {
    register<JvmTestSuite>("version3Test") {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-eventbus-3.0:testing"))

        val version = baseVersion("3.0.0").orLatest("3.+")
        implementation("io.vertx:vertx-core:$version")
        // FakeClusterManager for the clustered event bus tests
        implementation("io.vertx:vertx-core:$version:tests")
        compileOnly("io.vertx:vertx-codegen:$version")
        // not published for later 3.x versions
        compileOnly("io.vertx:vertx-docgen:3.0.0")
      }
    }

    // later 3.x changed reply handling and added MessageProducer.send(body, replyHandler)
    register<JvmTestSuite>("version39Test") {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-eventbus-3.0:testing"))

        val version = baseVersion("3.9.16").orLatest("3.+")
        implementation("io.vertx:vertx-core:$version")
        compileOnly("io.vertx:vertx-codegen:$version")
        compileOnly("io.vertx:vertx-docgen:3.0.0")
      }
    }

    register<JvmTestSuite>("version4Test") {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-eventbus-3.0:testing"))

        val version = baseVersion("4.0.0").orLatest("4.+")
        implementation("io.vertx:vertx-core:$version")
        // FakeClusterManager for the clustered event bus tests
        implementation("io.vertx:vertx-core:$version:tests")
        compileOnly("io.vertx:vertx-codegen:$version")
      }
    }

    register<JvmTestSuite>("version5Test") {
      dependencies {
        implementation(project(":instrumentation:vertx:vertx-eventbus-3.0:testing"))

        val version = baseVersion("5.0.0").orLatest()
        implementation("io.vertx:vertx-core:$version")
        // FakeClusterManager for the clustered event bus tests
        implementation("io.vertx:vertx-core:$version:tests")
        compileOnly("io.vertx:vertx-codegen:$version")
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
