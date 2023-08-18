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
  library("io.vertx:vertx-sql-client:4.0.0")
  compileOnly("io.vertx:vertx-codegen:4.0.0")

  // for hibernateReactive2Test
  testInstrumentation(project(":instrumentation:hibernate:hibernate-6.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testLibrary("io.vertx:vertx-pg-client:4.0.0")
  testLibrary("io.vertx:vertx-codegen:4.0.0")
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

testing {
  suites {
    val hibernateReactive1Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation("org.testcontainers:testcontainers")
        if (latestDepTest) {
          implementation("org.hibernate.reactive:hibernate-reactive-core:1.+")
          implementation("io.vertx:vertx-pg-client:+")
        } else {
          implementation("org.hibernate.reactive:hibernate-reactive-core:1.0.0.Final")
          implementation("io.vertx:vertx-pg-client:4.1.5")
        }
      }
    }

    val hibernateReactive2Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation("org.testcontainers:testcontainers")
        if (latestDepTest) {
          implementation("org.hibernate.reactive:hibernate-reactive-core:2.+")
          implementation("io.vertx:vertx-pg-client:+")
        } else {
          implementation("org.hibernate.reactive:hibernate-reactive-core:2.0.0.Final")
          implementation("io.vertx:vertx-pg-client:4.4.2")
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
  named("compileHibernateReactive2TestJava", JavaCompile::class).configure {
    options.release.set(11)
  }
  val testJavaVersion =
    gradle.startParameter.projectProperties.get("testJavaVersion")?.let(JavaVersion::toVersion)
      ?: JavaVersion.current()
  if (testJavaVersion.isJava8) {
    named("hibernateReactive2Test", Test::class).configure {
      enabled = false
    }
    if (latestDepTest) {
      named("hibernateReactive1Test", Test::class).configure {
        enabled = false
      }
    }
  }

  check {
    dependsOn(testing.suites)
  }
}
