plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-sql-client")
    versions.set("[4.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  val version = "4.0.0"
  library("io.vertx:vertx-sql-client:$version")
  compileOnly("io.vertx:vertx-codegen:$version")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testLibrary("io.vertx:vertx-pg-client:$version")
  testLibrary("io.vertx:vertx-codegen:$version")
}

testing {
  suites {
    val testVertx4_5 by registering(JvmTestSuite::class) {
      val version = "4.5.13"
      dependencies {
        implementation(project())
        implementation("io.vertx:vertx-sql-client:$version")
        implementation("io.vertx:vertx-codegen:$version")
        implementation("io.vertx:vertx-pg-client:$version")
        implementation("org.testcontainers:testcontainers")

        implementation(project(":instrumentation:netty:netty-4.1:javaagent"))
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.opt-in=database")
          }
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}

val latestDepTest = findProperty("testLatestDeps") as Boolean
if (!latestDepTest) {
  // https://bugs.openjdk.org/browse/JDK-8320431
  otelJava {
    maxJavaVersionForTests.set(JavaVersion.VERSION_21)
  }
}
